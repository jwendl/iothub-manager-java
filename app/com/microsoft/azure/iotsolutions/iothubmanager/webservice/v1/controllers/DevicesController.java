// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.webservice.v1.controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.google.inject.Inject;
import com.microsoft.azure.iotsolutions.iothubmanager.services.*;
import com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions.*;
import com.microsoft.azure.iotsolutions.iothubmanager.webservice.v1.models.*;
import play.libs.Json;
import play.mvc.*;

import java.util.concurrent.*;

import static play.libs.Json.fromJson;
import static play.libs.Json.toJson;

public final class DevicesController extends Controller {

    private final IDevices deviceService;
    private final ICache cacheService;
    final String ContinuationTokenName = "x-ms-continuation";

    @Inject
    public DevicesController(final IDevices deviceService, final ICache cacheService) {
        this.deviceService = deviceService;
        this.cacheService = cacheService;
    }

    public CompletionStage<Result> getDevicesAsync(String query) throws ExternalDependencyException {
        String continuationToken = "";
        if (request().getHeaders().contains(ContinuationTokenName)) {
            continuationToken = request().getHeaders().getAll(ContinuationTokenName).get(0);
        }
        return deviceService.queryAsync(query, continuationToken)
            .thenApply(devices -> ok(toJson(new DeviceListApiModel(devices))));
    }

    public CompletionStage<Result> queryDevicesAsync() throws ExternalDependencyException {
        String continuationToken = "";
        String query;
        if (request().getHeaders().get(CONTENT_TYPE).get().equals(Http.MimeTypes.JSON)) {
            query = Json.stringify(request().body().asJson());
        } else {
            query = request().body().asText();
        }

        if (request().getHeaders().contains(ContinuationTokenName)) {
            continuationToken = request().getHeaders().getAll(ContinuationTokenName).get(0);
        }

        return deviceService.queryAsync(query, continuationToken)
            .thenApply(devices -> ok(toJson(new DeviceListApiModel(devices))));
    }

    public CompletionStage<Result> getDeviceAsync(final String id) throws ExternalDependencyException {
        return deviceService.getAsync(id)
            .thenApply(device -> ok(toJson(new DeviceRegistryApiModel(device))));
    }

    public CompletionStage<Result> postAsync() throws InvalidInputException, ExternalDependencyException {
        JsonNode json = request().body().asJson();
        final DeviceRegistryApiModel device = fromJson(json, DeviceRegistryApiModel.class);
        return deviceService.createAsync(device.toServiceModel())
            .thenApply(newDevice -> ok(toJson(new DeviceRegistryApiModel(newDevice))));
    }

    public CompletionStage<Result> putAsync(final String id) throws InvalidInputException, ExternalDependencyException {
        JsonNode json = request().body().asJson();
        final DeviceRegistryApiModel device = fromJson(json, DeviceRegistryApiModel.class);
        ICache cacheService = this.cacheService;

        OnDeviceChange cacheUpdateCallBack = devices -> {
            try {
                cacheService.setCacheAsync(devices);
            } catch (BaseException | ExecutionException | InterruptedException e) {
                String message = String.format("Unable to update cache twin of device: %s", device.getId());
                if (e instanceof ExecutionException)
                    throw new CompletionException(
                        new ExecutionException(message, e));
                else if (e instanceof InterruptedException)
                    throw new CompletionException(
                        new InterruptedException(message));
                else
                    throw new CompletionException(
                        new BaseException(message, e));
            }
        };
        return deviceService.createOrUpdateAsync(id, device.toServiceModel(), cacheUpdateCallBack)
            .thenApply(newDevice -> ok(toJson(new DeviceRegistryApiModel(newDevice))));
    }

    public CompletionStage<Result> deleteAsync(final String id) throws ExternalDependencyException {
        return deviceService.deleteAsync(id)
            .thenApply(result -> ok());
    }

    public CompletionStage<Result> invokeDeviceMethodAsync(final String id) throws ExternalDependencyException {
        JsonNode json = request().body().asJson();
        final MethodParameterApiModel parameter = fromJson(json, MethodParameterApiModel.class);
        return deviceService.invokeDeviceMethodAsync(id, parameter.toServiceModel())
            .thenApply(result -> ok(toJson(new MethodResultApiModel(result))));
    }
}
