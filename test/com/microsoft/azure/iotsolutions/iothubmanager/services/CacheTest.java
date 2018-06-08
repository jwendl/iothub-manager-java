// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services;

import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.BaseException;
import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.ResourceNotFoundException;
import com.microsoft.azure.iotsolutions.iothubmanager.services.external.IStorageAdapterClient;
import com.microsoft.azure.iotsolutions.iothubmanager.services.external.ValueApiModel;
import com.microsoft.azure.iotsolutions.iothubmanager.services.models.CacheValue;
import com.microsoft.azure.iotsolutions.iothubmanager.services.models.DeviceTwinName;
import com.microsoft.azure.iotsolutions.iothubmanager.services.runtime.IServicesConfig;
import com.microsoft.azure.iotsolutions.iothubmanager.services.runtime.ServicesConfig;
import helpers.UnitTest;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.mockito.Mockito;
import play.libs.Json;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

import static org.junit.Assert.*;

public class CacheTest {
    private IStorageAdapterClient mockStorageAdapterClient;
    private IDevices mockDevices;
    private String cacheModel = "";
    private IServicesConfig config;
    private Cache cache;
    private HashMap<String, String> metadata = null;

    @Before
    public void setUp() {
        metadata = new HashMap<>();
        metadata.put("$modified", DateTime.now().plusDays(2).toString("yyyy-MM-dd'T'HH:mm:ssZZ"));
        mockStorageAdapterClient = Mockito.mock(IStorageAdapterClient.class);
        mockDevices = Mockito.mock(IDevices.class);
        cacheModel = "{\"Rebuilding\": false,\"Tags\": [ \"c\", \"a\", \"y\", \"z\" ],\"Reported\": [\"1\",\"9\",\"2\",\"3\"] }";
        List<String> cacheWhiteList = Arrays.asList("tags.*", "reported.*");
        config = new ServicesConfig(null, null, 0, 0, cacheWhiteList);
    }

    @Test(timeout = 100000)
    @Category({UnitTest.class})
    public void getCacheAsyncTestAsync() throws BaseException, ExecutionException, InterruptedException {
        Mockito.when(mockStorageAdapterClient.getAsync(Mockito.any(String.class), Mockito.any(String.class)))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", this.cacheModel, "", metadata)));
        cache = new Cache(mockStorageAdapterClient, config, mockDevices);
        CacheValue result = this.cache.getCacheAsync().toCompletableFuture().get();
        assertEquals(String.join(",", new TreeSet<String>(result.getTags())), "a,c,y,z");
        assertEquals(String.join(",", new TreeSet<String>(result.getReported())), "1,2,3,9");
    }

    @Test(timeout = 100000)
    @Category({UnitTest.class})
    public void setCacheAsyncTestAsync() throws BaseException, ExecutionException, InterruptedException {
        Mockito.when(mockStorageAdapterClient.getAsync(Mockito.any(String.class), Mockito.any(String.class)))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", this.cacheModel, "", metadata)));
        CacheValue resultModel = new CacheValue(new HashSet<String>(Arrays.asList("c", "a", "y", "z", "@", "#")),
            new HashSet<String>(Arrays.asList("1", "9", "2", "3", "12", "11")), false);
        CacheValue model = new CacheValue(new HashSet<String>(Arrays.asList("a", "y", "z", "@", "#")),
            new HashSet<String>(Arrays.asList("9", "2", "3", "11", "12")), false);
        Mockito.when(mockStorageAdapterClient.updateAsync(Mockito.any(String.class), Mockito.any(String.class),
            Mockito.any(String.class), Mockito.any(String.class)))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", Json.stringify(Json.toJson(resultModel)), "", null)));
        cache = new Cache(mockStorageAdapterClient, config, mockDevices);
        CacheValue result = this.cache.setCacheAsync(model).toCompletableFuture().get();
        assertEquals(String.join(",", new TreeSet<String>(result.getTags())), "#,@,a,c,y,z");
        assertEquals(String.join(",", new TreeSet<String>(result.getReported())), "1,11,12,2,3,9");
    }

    @Test(timeout = 300000)
    @Category({UnitTest.class})
    public void rebuildCacheAsyncSuccessTestAsync() throws Exception {
        Mockito.when(mockDevices.GetDeviceTwinNames())
            .thenReturn(new DeviceTwinName(new HashSet<>(), new HashSet<>()))
            .thenReturn(new DeviceTwinName(new HashSet<>(Arrays.asList("tags.FieldService")), new HashSet<>()));

        Mockito.when(mockStorageAdapterClient.getAsync(Mockito.any(String.class), Mockito.any(String.class)))
            .thenThrow(new ResourceNotFoundException("The cache is not found"))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", this.cacheModel, "", metadata)));
        CacheValue emptyCacheValue = new CacheValue(new HashSet<>(), new HashSet<>(), false);
        CacheValue noneEmptyCacheValue = new CacheValue(new HashSet<>(Arrays.asList("tags.FieldService")), new HashSet<>(Arrays.asList("reported.SupportedMethods")), false);
        Mockito.when(mockStorageAdapterClient.updateAsync(Mockito.any(String.class), Mockito.any(String.class),
            Mockito.any(String.class), Mockito.any()))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", Json.stringify(Json.toJson(emptyCacheValue)), "", null)))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", Json.stringify(Json.toJson(noneEmptyCacheValue)), "", null)));

        cache = new Cache(mockStorageAdapterClient, config, mockDevices);
        Boolean result = (Boolean) this.cache.rebuildCacheAsync(false).toCompletableFuture().get();
        assertTrue(result);
    }

    @Test(timeout = 100000)
    @Category({UnitTest.class})
    public void rebuildCacheAsyncFailureTestAsync() throws Exception {
        Mockito.when(mockDevices.GetDeviceTwinNames())
            .thenReturn(new DeviceTwinName(new HashSet<>(Arrays.asList("tags.FieldService")), new HashSet<>()));
        String rebuildingCacheModel = "{\"Rebuilding\": true,\"Tags\": null,\"Reported\": null }";
        Mockito.when(mockStorageAdapterClient.getAsync(Mockito.any(String.class), Mockito.any(String.class)))
            .thenReturn(CompletableFuture.supplyAsync(() -> new ValueApiModel("", rebuildingCacheModel, "", metadata)));
        cache = new Cache(mockStorageAdapterClient, config, mockDevices);
        Boolean result = (Boolean) this.cache.rebuildCacheAsync(false).toCompletableFuture().get();
        assertFalse(result);
    }
}
