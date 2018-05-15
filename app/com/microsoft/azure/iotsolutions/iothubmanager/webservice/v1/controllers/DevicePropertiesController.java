// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.webservice.v1.controllers;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.microsoft.azure.iotsolutions.iothubmanager.services.ICache;
import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.BaseException;
import com.microsoft.azure.iotsolutions.iothubmanager.webservice.v1.models.DevicePropertiesApiModel;
import play.mvc.Controller;
import play.mvc.Result;

import java.util.concurrent.CompletionStage;

import static play.libs.Json.toJson;

@Singleton
public class DevicePropertiesController extends Controller {
    private final ICache cache;

    @Inject
    public DevicePropertiesController(ICache cache) {
        this.cache = cache;
    }

    public CompletionStage<Result> getAllAsync() throws BaseException {
        return cache.getCacheAsync().thenApplyAsync(m ->
                ok(toJson(new DevicePropertiesApiModel(m)))
        );
    }
}
