package com.nanogate.routing.config;

/**
 * Strategy interface for providing NanoGate routing configurations.
 * Allows implementations to fetch configurations from various sources (Spring Native, Yaml Files, Azure, AWS, etc.).
 */
public interface ConfigurationProvider {
    /**
     * Fetches the current configuration.
     * @return the configuration properties, or null if this provider is currently unavailable.
     */
    NanoGateRouteProperties fetchConfiguration();
}
