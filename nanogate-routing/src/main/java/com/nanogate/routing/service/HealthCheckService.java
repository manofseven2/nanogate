package com.nanogate.routing.service;

import java.net.URI;

/**
 * An abstraction for checking the health of a backend server.
 * This allows for multiple health check strategies (e.g., active, passive) to be used interchangeably.
 */
public interface HealthCheckService {

    /**
     * Checks if a given backend server is currently considered healthy.
     *
     * @param serverUri The URI of the server to check.
     * @return {@code true} if the server is healthy, {@code false} otherwise.
     */
    boolean isHealthy(URI serverUri);

    /**
     * Manually marks a backend server as unhealthy.
     * This is typically called by a passive health check mechanism (like a circuit breaker)
     * to provide immediate feedback to the health registry.
     *
     * @param serverUri The URI of the server to mark as unhealthy.
     */
    void markAsUnhealthy(URI serverUri);
}
