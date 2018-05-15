package com.microsoft.azure.iotsolutions.iothubmanager.services;

import com.microsoft.azure.iotsolutions.iothubmanager.services.models.CacheValue;

import java.util.concurrent.CompletionStage;

public interface OnDeviceChange {
    public CompletionStage updateCache(CacheValue devices);
}
