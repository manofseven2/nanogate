package com.nanogate.routing.model;

import java.net.URI;
import java.util.List;

/**
 * Represents a configured pool of backend servers with a default load balancing strategy
 * and default HTTP client properties.
 */
public class BackendSet {
    private String name;
    private String loadBalancer;
    private List<URI> servers;
    private HttpClientProperties httpClient;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(String loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public List<URI> getServers() {
        return servers;
    }

    public void setServers(List<URI> servers) {
        this.servers = servers;
    }

    public HttpClientProperties getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClientProperties httpClient) {
        this.httpClient = httpClient;
    }
}