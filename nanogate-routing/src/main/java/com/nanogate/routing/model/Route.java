package com.nanogate.routing.model;

/**
 * Represents a routing rule that maps a path to a named BackendSet.
 * It can optionally override policies defined in the BackendSet, such as the load balancer
 * or HTTP client settings.
 */
public class Route {
    private String id;
    private String path;
    private String backendSet; // Reverted to String
    private String loadBalancer; // Optional override
    private HttpClientProperties httpClient; // Optional override

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getBackendSet() {
        return backendSet;
    }

    public void setBackendSet(String backendSet) {
        this.backendSet = backendSet;
    }

    public String getLoadBalancer() {
        return loadBalancer;
    }

    public void setLoadBalancer(String loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    public HttpClientProperties getHttpClient() {
        return httpClient;
    }

    public void setHttpClient(HttpClientProperties httpClient) {
        this.httpClient = httpClient;
    }
}