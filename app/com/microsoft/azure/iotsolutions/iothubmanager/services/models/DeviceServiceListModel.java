// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.microsoft.azure.iotsolutions.iothubmanager.services.helpers.HashSetHelper;

import java.util.HashSet;
import java.util.List;
import java.util.stream.StreamSupport;

public class DeviceServiceListModel {

    private String continuationToken;
    private List<DeviceServiceModel> items;

    public DeviceServiceListModel(List<DeviceServiceModel> devices, String continuationToken) {
        this.continuationToken = continuationToken;
        this.items = devices;
    }

    @JsonProperty("Items")
    public List<DeviceServiceModel> getItems() {
        return this.items;
    }

    public String getContinuationToken() {
        return this.continuationToken;
    }

    public DeviceTwinName GetDeviceTwinNames() {
        DeviceTwinName twinNames = new DeviceTwinName();
        HashSet<String> tagSet = new HashSet<>();
        StreamSupport.stream(items.spliterator(), false).forEach(m -> {
            m.getTwin().getTags().entrySet().stream().forEach(n -> {
                HashSetHelper.preparePropNames(tagSet, n.toString(), n.toString());
            });
        });
        HashSet<String> reportedSet = new HashSet<>();
        StreamSupport.stream(items.spliterator(), false).forEach(m -> {
            m.getTwin().getProperties().getReported().entrySet().stream().forEach(n -> {
                HashSetHelper.preparePropNames(tagSet, n.getValue(), n.getKey());
            });
        });
        return twinNames;
    }
}
