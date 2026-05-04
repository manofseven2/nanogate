package com.nanogate.routing.config;

import org.springframework.context.ApplicationEvent;

/**
 * Event published when the NanoGate routing configuration has been hot-swapped.
 */
public class ConfigurationRefreshedEvent extends ApplicationEvent {

    private final NanoGateRouteProperties newConfig;

    public ConfigurationRefreshedEvent(Object source, NanoGateRouteProperties newConfig) {
        super(source);
        this.newConfig = newConfig;
    }

    public NanoGateRouteProperties getNewConfig() {
        return newConfig;
    }
}
