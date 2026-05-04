package com.nanogate.routing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe wrapper holding the current active routing configuration.
 * Components should call get() to fetch a stable snapshot of the routes for the duration of an operation.
 */
@Component
public class RouteRegistry {
    private static final Logger log = LoggerFactory.getLogger(RouteRegistry.class);
    
    private final AtomicReference<NanoGateRouteProperties> currentConfig = new AtomicReference<>();

    public RouteRegistry(NanoGateRouteProperties initialConfig) {
        this.currentConfig.set(initialConfig);
        log.info("RouteRegistry initialized with {} backend-sets and {} routes", 
                initialConfig.getBackendSets().size(), 
                initialConfig.getRoutes().size());
    }

    /**
     * @return The currently active routing properties.
     */
    public NanoGateRouteProperties get() {
        return currentConfig.get();
    }

    /**
     * Safely updates the global routing configuration.
     * @param newConfig The new configuration to apply.
     */
    public void update(NanoGateRouteProperties newConfig) {
        currentConfig.set(newConfig);
        log.info("RouteRegistry hot-swapped configuration. New sizes: {} backend-sets, {} routes",
                newConfig.getBackendSets().size(),
                newConfig.getRoutes().size());
    }
}
