// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.iothubmanager.services.exceptions;

/**
 * This exception is thrown when a client is requesting a resource that
 * doesn't exist yet.
 */
public class ResourceNotFoundException extends BaseException {
    public ResourceNotFoundException() {
    }

    public ResourceNotFoundException(String message) {
        super(message);
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
