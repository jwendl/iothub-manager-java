// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.iotsolutions.iothubmanager.services.runtime.IServicesConfig;
import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.*;
import com.microsoft.azure.iotsolutions.iothubmanager.services.external.*;
import com.microsoft.azure.iotsolutions.iothubmanager.services.helpers.StorageWriteLock;
import com.microsoft.azure.iotsolutions.iothubmanager.services.models.CacheValue;
import com.microsoft.azure.iotsolutions.iothubmanager.services.models.DeviceTwinName;
import org.joda.time.DateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import play.Logger;
import play.libs.Json;

import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

@Singleton
public class Cache implements ICache {

    private final IStorageAdapterClient storageClient;
    private static final Logger.ALogger log = Logger.of(Cache.class);
    private final int cacheTTL;
    private final int rebuildTimeout;
    private final String CacheCollectionId = "cache";
    private final String CacheKey = "twin";
    private final List<String> cacheWhitelist;
    private static final String WHITELIST_TAG_PREFIX = "tags.";
    private static final String WHITELIST_REPORTED_PREFIX = "reported.";
    private static final long SERVICE_QUERY_INTERVAL_SECS = 10;
    private static IDevices devices;

    @Inject
    public Cache(IStorageAdapterClient storageClient,
                 IServicesConfig config,
                 IDevices devices) throws ExternalDependencyException {
        this.storageClient = storageClient;
        this.cacheTTL = config.getCacheTTL();
        this.rebuildTimeout = config.getCacheRebuildTimeout();
        this.cacheWhitelist = config.getCacheWhiteList();
        this.devices = devices;
    }

    @Override
    public CompletionStage<CacheValue> getCacheAsync() {
        try {
            return storageClient.getAsync(CacheCollectionId, CacheKey).thenApplyAsync(m ->
                Json.fromJson(Json.parse(m.getData()), CacheValue.class)
            );
        } catch (Exception ex) {
            log.info(String.format("%s:%s not found.", CacheCollectionId, CacheKey), ex);
            return CompletableFuture.completedFuture(new CacheValue(new HashSet<>(), new HashSet<>()));
        }
    }

    @Override
    public CompletionStage<CacheValue> setCacheAsync(CacheValue cacheValuesToAdd) throws BaseException {
        if (cacheValuesToAdd.getReported() == null) {
            cacheValuesToAdd.setReported(new HashSet<>());
        }
        if (cacheValuesToAdd.getTags() == null) {
            cacheValuesToAdd.setTags(new HashSet<>());
        }
        String etag = null;
        while (true) {
            ValueApiModel model = this.getCurrentCacheFromStorage();
            if (model != null) {
                etag = model.getETag();
                CacheValue cacheServer = Json.fromJson(Json.parse(model.getData()), CacheValue.class);
                this.updateCacheValues(model, cacheValuesToAdd);
                if (cacheValuesToAdd.getTags().size() == cacheServer.getTags().size() && cacheValuesToAdd.getReported().size() == cacheServer.getReported().size()) {
                    return CompletableFuture.completedFuture(cacheValuesToAdd);
                }
            }

            String value = Json.stringify(Json.toJson(cacheValuesToAdd));
            try {
                return this.storageClient.updateAsync(CacheCollectionId, CacheKey, value, etag).thenApplyAsync(m ->
                    Json.fromJson(Json.parse(m.getData()), CacheValue.class)
                );
            } catch (ConflictingResourceException e) {
                log.info("SetCacheAsync Conflicted ");
                continue;
            }
        }
    }

    @Override
    public CompletionStage rebuildCacheAsync(boolean force) throws Exception {
        StorageWriteLock<CacheValue> lock = new StorageWriteLock<>(
            CacheValue.class,
            this.storageClient,
            CacheCollectionId,
            CacheKey,
            (c, b) -> c.setRebuilding(b),
            m -> this.shouldRebuild(force, m));

        while (true) {
            // Try to read non-empty twin data at first before locking cache entry
            // to improve lock condition in case lock has been acquired but twin data
            // might be still unavailable. When cache data is available, it will be
            // safer to write an empty cache data in order to acquire lock and then
            // update twin data into the cache entry.
            DeviceTwinName twinNames = null;
            try {
                twinNames = this.getDevicePropertyNames();
                if (twinNames.isEmpty()) {
                    this.log.info(String.format("There is no property available to be cached. Retry after %d seconds", this.SERVICE_QUERY_INTERVAL_SECS));
                    Thread.sleep(this.SERVICE_QUERY_INTERVAL_SECS * 1000);
                    continue;
                }
            } catch (Exception e) {
                this.log.warn("Some underlying service is not ready. Retry after " + this.SERVICE_QUERY_INTERVAL_SECS);
                Thread.sleep(this.SERVICE_QUERY_INTERVAL_SECS * 1000);
                continue;
            }

            Optional<Boolean> locked = this.lockCache(lock);
            if (locked == null) {
                this.log.warn(String.format("Cache rebuilding: lock failed due to conflict. Retry after %d seconds", this.SERVICE_QUERY_INTERVAL_SECS));
                Thread.sleep(this.SERVICE_QUERY_INTERVAL_SECS * 1000);
                continue;
            }
            if (!locked.get()) {
                return CompletableFuture.completedFuture(false);
            }

            Boolean updated = this.writeAndUnlockCache(lock, twinNames);

            if (updated) {
                return CompletableFuture.completedFuture(true);
            }

            this.log.warn("The cache failed to be written due to conflict. Retry soon");
        }
    }

    private boolean shouldRebuild(boolean force, ValueApiModel twin) {
        if (force) {
            this.log.info("Cache will be rebuilt due to the force flag");
            return true;
        }

        if (twin == null) {
            this.log.info("Cache will be rebuilt since no cache was found");
            return true;
        }

        CacheValue cacheValue;
        try {
            cacheValue = Json.fromJson(Json.parse(twin.getData()), CacheValue.class);
        } catch (Exception e) {
            this.log.info("Cache will be rebuilt since the last one is broken.");
            return true;
        }

        DateTimeFormatter formatter = DateTimeFormat.forPattern("yyyy-MM-dd'T'HH:mm:ssZZ");
        DateTime timestamp = formatter.parseDateTime(twin.getMetadata().get("$modified"));
        if (cacheValue.isRebuilding()) {
            if (timestamp.plusSeconds(this.rebuildTimeout).isBeforeNow()) {
                this.log.info("Cache will be rebuilt since last rebuilding timedout");
                return true;
            }
            this.log.info("Cache rebuilding skipped since it was being rebuilt by other instance");
            return false;
        } else if (cacheValue.isNullOrEmpty()) {
            this.log.info("Cache will be rebuilt since it is empty");
            return true;
        } else if (timestamp.plusSeconds(this.cacheTTL).isBeforeNow()) {
            this.log.info("Cache will be rebuilt since it has expired");
            return true;
        } else {
            this.log.info("Cache rebuilding skipped since it has not expired");
            return false;
        }
    }

    private CompletionStage<DeviceTwinName> getValidNamesAsync() throws ExternalDependencyException {
        DeviceTwinName fullNameWhitelist = new DeviceTwinName(), prefixWhitelist = new DeviceTwinName();
        this.parseWhitelist(this.cacheWhitelist, fullNameWhitelist, prefixWhitelist);

        DeviceTwinName validNames = new DeviceTwinName(fullNameWhitelist.getTags(), fullNameWhitelist.getReportedProperties());

        if (!prefixWhitelist.getTags().isEmpty() || !prefixWhitelist.getReportedProperties().isEmpty()) {
            DeviceTwinName allNames = devices.GetDeviceTwinNames();
            validNames.getTags().addAll(allNames.getTags().stream().
                filter(m -> prefixWhitelist.getTags().stream().anyMatch(m::startsWith)).collect(Collectors.toSet()));

            validNames.getReportedProperties().addAll(allNames.getReportedProperties().stream().
                filter(m -> prefixWhitelist.getReportedProperties().stream().anyMatch(m::startsWith)).collect(Collectors.toSet()));
        }

        return CompletableFuture.supplyAsync(() -> validNames);
    }

    private void parseWhitelist(List<String> whitelist, DeviceTwinName fullNameWhitelist, DeviceTwinName prefixWhitelist) {

        List<String> tags = whitelist.stream().filter(m -> m.toLowerCase().startsWith(WHITELIST_TAG_PREFIX)).
            map(m -> m.substring(WHITELIST_TAG_PREFIX.length())).collect(Collectors.toList());

        List<String> reported = whitelist.stream().filter(m -> m.toLowerCase().startsWith(WHITELIST_REPORTED_PREFIX)).
            map(m -> m.substring(WHITELIST_REPORTED_PREFIX.length())).collect(Collectors.toList());

        List<String> fixedTags = tags.stream().filter(m -> !m.endsWith("*")).collect(Collectors.toList());
        List<String> fixedReported = reported.stream().filter(m -> !m.endsWith("*")).collect(Collectors.toList());
        List<String> regexTags = tags.stream().filter(m -> m.endsWith("*")).
            map(m -> m.substring(0, m.length() - 1)).collect(Collectors.toList());

        List<String> regexReported = reported.stream().filter(m -> m.endsWith("*")).
            map(m -> m.substring(0, m.length() - 1)).collect(Collectors.toList());

        fullNameWhitelist.setTags(new HashSet<>(fixedTags));
        fullNameWhitelist.setReportedProperties(new HashSet<>(fixedReported));
        prefixWhitelist.setTags(new HashSet<>(regexTags));
        prefixWhitelist.setReportedProperties(new HashSet<>(regexReported));
    }

    private ValueApiModel getCurrentCacheFromStorage() throws ExternalDependencyException {
        try {
            return this.storageClient.getAsync(CacheCollectionId, CacheKey).toCompletableFuture().get();
        } catch (ResourceNotFoundException e) {
            log.info(String.format("SetCacheAsync %s:%s not found.", CacheCollectionId, CacheKey));
        } catch (InterruptedException | ExecutionException | BaseException e) {
            log.error(String.format("SetCacheAsync InterruptedException occurred in storageClient.getAsync(%s, %s).", CacheCollectionId, CacheKey));
            throw new ExternalDependencyException("SetCacheAsync failed");
        }
        return null;
    }

    private void updateCacheValues(ValueApiModel cacheFromStorage, CacheValue cacheValuesToAdd) {
        if (cacheFromStorage != null) {
            CacheValue cacheServer;
            try {
                cacheServer = Json.fromJson(Json.parse(cacheFromStorage.getData()), CacheValue.class);
            } catch (Exception e) {
                cacheServer = new CacheValue();
            }
            if (cacheServer.getTags() == null) {
                cacheServer.setTags(new HashSet<String>());
            }
            if (cacheServer.getReported() == null) {
                cacheServer.setReported(new HashSet<String>());
            }
            cacheValuesToAdd.getTags().addAll(cacheServer.getTags());
            cacheValuesToAdd.getReported().addAll(cacheServer.getReported());
        }
    }

    private Optional<Boolean> lockCache(StorageWriteLock<CacheValue> lock) throws ExternalDependencyException, ResourceOutOfDateException {
        try {
            return lock.tryLockAsync().toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ExternalDependencyException("failed to lock");
        }
    }

    private DeviceTwinName getDevicePropertyNames() throws ExternalDependencyException, URISyntaxException, ExecutionException, InterruptedException {
        CompletableFuture<DeviceTwinName> twinNamesTask = this.getValidNamesAsync().toCompletableFuture();
        DeviceTwinName twinNames = twinNamesTask.get();
        return twinNames;
    }

    private Boolean writeAndUnlockCache(StorageWriteLock<CacheValue> lock, DeviceTwinName twinNames) throws ExternalDependencyException, ResourceOutOfDateException {
        try {
            return lock.writeAndReleaseAsync(new CacheValue(twinNames.getTags(), twinNames.getReportedProperties())).toCompletableFuture().get();
        } catch (InterruptedException | ExecutionException e) {
            String errorMessage = String.format("failed to WriteAndRelease lock for %s,%s ", CacheCollectionId, CacheKey);
            this.log.error(errorMessage, e);
            throw new ExternalDependencyException(errorMessage);
        }
    }
}
