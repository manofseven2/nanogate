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
    private String backendSet;
    private String loadBalancer;
    private HttpClientProperties httpClient;
    private ResilienceProperties resilience;
    private HeaderTransformProperties requestHeaders;
    private HeaderTransformProperties responseHeaders;
    private Integer stripPrefix;
    private String rewritePath;
    private String rewriteReplacement;
    private String ipSet;
    private RateLimitProperties rateLimit;

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

    public Integer getStripPrefix() {
        return stripPrefix;
    }

    public void setStripPrefix(Integer stripPrefix) {
        this.stripPrefix = stripPrefix;
    }

    public String getRewritePath() {
        return rewritePath;
    }

    public void setRewritePath(String rewritePath) {
        this.rewritePath = rewritePath;
    }

    public String getRewriteReplacement() {
        return rewriteReplacement;
    }

    public void setRewriteReplacement(String rewriteReplacement) {
        this.rewriteReplacement = rewriteReplacement;
    }

    public String getIpSet() {
        return ipSet;
    }

    public void setIpSet(String ipSet) {
        this.ipSet = ipSet;
    }

    public RateLimitProperties getRateLimit() {
        return rateLimit;
    }

    public void setRateLimit(RateLimitProperties rateLimit) {
        this.rateLimit = rateLimit;
    }
}
