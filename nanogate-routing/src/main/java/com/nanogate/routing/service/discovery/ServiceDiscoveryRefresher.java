package com.nanogate.routing.service.discovery;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.config.RouteRegistry;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.DiscoveryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * Background service that periodically refreshes the service discovery cache
 * for all dynamic backend sets. This ensures that IP addresses are pre-resolved
 * and available in the cache, eliminating discovery latency during request processing.
 */
@Service
public class ServiceDiscoveryRefresher {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryRefresher.class);

    private final RouteRegistry routeRegistry;
    private final ServiceDiscoveryRegistry discoveryRegistry;

    public ServiceDiscoveryRefresher(RouteRegistry routeRegistry, ServiceDiscoveryRegistry discoveryRegistry) {
        this.routeRegistry = routeRegistry;
        this.discoveryRegistry = discoveryRegistry;
    }

    /**
     * Periodically refreshes dynamic backend sets.
     * Fixed delay can be configured; defaults to 5 seconds.
     */
    @Scheduled(fixedDelayString = "${nanogate.routing.discovery.refresh-interval:5000}")
    public void refreshDynamicBackends() {
        NanoGateRouteProperties config = routeRegistry.get();
        if (config == null || config.getBackendSets() == null) {
            return;
        }

        log.trace("Starting background service discovery refresh cycle...");

        for (BackendSet backendSet : config.getBackendSets()) {
            // Only pre-emptively refresh dynamic discovery types.
            // STATIC types never change their IPs at the discovery level (only via config reload).
            if (backendSet.getDiscoveryType() != null && backendSet.getDiscoveryType() != DiscoveryType.STATIC) {
                log.debug("Pre-emptively refreshing discovery cache for backend set: {}", backendSet.getName());
                try {
                    discoveryRegistry.updateCache(backendSet);
                } catch (Exception e) {
                    log.error("Failed to background refresh discovery cache for backend set: {}", backendSet.getName(), e);
                }
            }
        }
    }
}
