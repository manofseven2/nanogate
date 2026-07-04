package com.nanogate.routing.model;

import java.time.Duration;

/**
 * Configuration properties for Service Discovery.
 */
public class DiscoveryProperties {

    /**
     * Interval for pre-emptively refreshing discovery caches in the background.
     * Defaults to 5 seconds.
     */
    private Duration refreshInterval = Duration.ofSeconds(5);
    private ConsulDiscoveryProperties consul = new ConsulDiscoveryProperties();

    public Duration getRefreshInterval() {
        return refreshInterval;
    }

    public void setRefreshInterval(Duration refreshInterval) {
        this.refreshInterval = refreshInterval;
    }

    public ConsulDiscoveryProperties getConsul() {
        return consul;
    }

    public void setConsul(ConsulDiscoveryProperties consul) {
        this.consul = consul;
    }
}
