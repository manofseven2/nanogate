package com.nanogate.routing.model;

/**
 * Configuration properties for rate limiting.
 */
public class RateLimitProperties {

    /**
     * The maximum number of requests allowed per second.
     */
    private Integer requestsPerSecond;

    /**
     * The name of the KeyResolver strategy to use. Defaults to "IP".
     * Other options might include "HEADER", "PRINCIPAL", etc.
     */
    private String resolver = "IP";

    /**
     * An optional argument for the resolver.
     * For example, if resolver is "HEADER", resolverArg would be the name of the header (e.g., "X-API-Key").
     */
    private String resolverArg;

    public Integer getRequestsPerSecond() {
        return requestsPerSecond;
    }

    public void setRequestsPerSecond(Integer requestsPerSecond) {
        this.requestsPerSecond = requestsPerSecond;
    }

    public String getResolver() {
        return resolver;
    }

    public void setResolver(String resolver) {
        this.resolver = resolver;
    }

    public String getResolverArg() {
        return resolverArg;
    }

    public void setResolverArg(String resolverArg) {
        this.resolverArg = resolverArg;
    }
}
