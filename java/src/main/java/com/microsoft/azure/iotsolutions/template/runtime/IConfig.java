// Copyright (c) Microsoft. All rights reserved.

package com.microsoft.azure.iotsolutions.template.runtime;

public interface IConfig {
    /**
     * Get the TCP port number where the service listen for requests.
     *
     * @return TCP port number
     */
    int getWebServicePort();

    /**
     * Get the hostname where the service listen for requests, e.g. 0.0.0.0 when listening to all
     * the network adapters.
     *
     * @return Hostname or IP address
     */
    String getHostname();
}
