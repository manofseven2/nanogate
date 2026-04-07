package com.nanogate.routing.model;

import java.time.Duration;

/**
 * Configuration for active health checking of backend servers.
 *
 * @param path The path to ping for a health check (e.g., "/health").
 * @param interval The frequency at which to perform the health check.
 * @param timeout The maximum time to wait for a response from the health check endpoint.
 */
public record HealthCheckProperties(
        String path,
        Duration interval,
        Duration timeout
) {
    // Default values can be handled in configuration properties binding
}
