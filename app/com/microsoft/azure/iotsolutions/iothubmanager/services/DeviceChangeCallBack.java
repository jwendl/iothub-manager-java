package com.microsoft.azure.iotsolutions.iothubmanager.services;

import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.BaseException;
import com.microsoft.azure.iotsolutions.iothubmanager.services.models.CacheValue;

import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

public interface DeviceChangeCallBack {
    public CompletionStage updateCache(CacheValue devices) throws InterruptedException, ExecutionException, BaseException, BaseException;
}
