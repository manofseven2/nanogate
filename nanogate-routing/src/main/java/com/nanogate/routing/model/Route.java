package com.nanogate.routing.model;

import com.nanogate.resilience.model.ResilienceProperties;

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
    private ResilienceProperties resilience; // Optional override
    private HeaderTransformProperties requestHeaders;
    private HeaderTransformProperties responseHeaders;

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

    public ResilienceProperties getResilience() {
        return resilience;
    }

    public void setResilience(ResilienceProperties resilience) {
        this.resilience = resilience;
    }

    public HeaderTransformProperties getRequestHeaders() {
        return requestHeaders;
    }

    public void setRequestHeaders(HeaderTransformProperties requestHeaders) {
        this.requestHeaders = requestHeaders;
    }

    public HeaderTransformProperties getResponseHeaders() {
        return responseHeaders;
    }

    public void setResponseHeaders(HeaderTransformProperties responseHeaders) {
        this.responseHeaders = responseHeaders;
    }
}
