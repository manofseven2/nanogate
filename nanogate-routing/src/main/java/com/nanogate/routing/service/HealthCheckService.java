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
}
