package com.nanogate.resilience.service;

/**
 * Service responsible for enforcing rate limits.
 * Extracted as an interface to support both in-memory and distributed (e.g., Redis) implementations.
 */
public interface RateLimiterService {

    /**
     * Attempts to acquire permission for a request.
     *
     * @param key               The unique key identifying the rate limit bucket (e.g., "route-id:client-ip").
     * @param requestsPerSecond The maximum number of requests allowed per second.
     * @return true if the request is allowed, false if the rate limit is exceeded.
     */
    boolean acquirePermission(String key, int requestsPerSecond);
}
