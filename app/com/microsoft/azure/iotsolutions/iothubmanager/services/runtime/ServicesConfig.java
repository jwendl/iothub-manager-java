// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services.runtime;

import java.util.List;

/**
 * Service layer configuration
 */
public class ServicesConfig implements IServicesConfig {

    private String hubConnString;
    private String storageAdapterServiceUrl;
    private int cacheTTL;
    private int cacheRebuildTimeout;
    private List<String> cacheWhiteList;

    public ServicesConfig(final String hubConnString, final String storageAdapterApiUrl,
                          int cacheTTL, int cacheRebuildTimeout, List<String> cacheWhiteList) {
        this.hubConnString = hubConnString;
        this.storageAdapterServiceUrl = storageAdapterServiceUrl;
        this.cacheWhiteList = cacheWhiteList;
        this.cacheTTL = cacheTTL;
        this.cacheRebuildTimeout = cacheRebuildTimeout;
    }

    /**
     * Get Azure IoT Hub connection string.
     *
     * @return Connection string
     */
    public String getHubConnString() {
        return this.hubConnString;
    }

    /**
     * Get Storage Adapter service URL.
     *
     * @return Storage Adapter service URL
     */
    @Override
    public String getStorageAdapterServiceUrl() {
        return storageAdapterServiceUrl;
    }

    @Override
    public int getCacheTTL() {
        return cacheTTL;
    }

    @Override
    public int getCacheRebuildTimeout() {
        return cacheRebuildTimeout;
    }

    @Override
    public List<String> getCacheWhiteList() {
        return cacheWhiteList;
    }
}
