package com.microsoft.azure.iotsolutions.iothubmanager.services;

import com.microsoft.azure.iotsolutions.iothubmanager.services.models.CacheValue;

public interface OnDeviceChange {
    public void updateCache(CacheValue devices);
}
