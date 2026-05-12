package com.nanogate.routing.model;

/**
 * Configuration properties for the native Consul Service Discovery provider.
 */
public class ConsulDiscoveryProperties {
    
    private String host = "localhost";
    private int port = 8500;

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }
}
