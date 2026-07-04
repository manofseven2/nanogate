package com.nanogate.routing.service.discovery;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.nanogate.routing.model.BackendSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * The central component that delegates server resolution to the appropriate ServiceDiscoveryProvider.
 * Utilizes a high-performance Caffeine cache to ensure sub-millisecond resolution latency
 * and prevent overwhelming external registries (Consul, K8s, DNS) on every API request.
 */
@Service
public class ServiceDiscoveryRegistry {

    private static final Logger log = LoggerFactory.getLogger(ServiceDiscoveryRegistry.class);

    private final List<ServiceDiscoveryProvider> providers;
    
    // Short-lived cache (5 seconds) to balance dynamic responsiveness with high performance
    private final Cache<String, List<URI>> resolutionCache;

    public ServiceDiscoveryRegistry(List<ServiceDiscoveryProvider> providers) {
        this.providers = providers;
        this.resolutionCache = Caffeine.newBuilder()
                .expireAfterWrite(5, TimeUnit.SECONDS)
                .maximumSize(1000)
                .build();
    }

    /**
     * Resolves the backend set to a list of server URIs.
     * Uses the 5-second cache to return instantly under high load.
     * 
     * @param backendSet The configured backend set
     * @return List of URIs
     */
    public List<URI> resolve(BackendSet backendSet) {
        // Cache key incorporates the backend set name and its discovery type/serviceId
        // This ensures compatibility with the Zero-Downtime Config Reloading:
        // if the config file changes a backend set from STATIC to CONSUL, the cache key changes or expires.
        String cacheKey = backendSet.getName() + ":" + backendSet.getDiscoveryType() + ":" + backendSet.getServiceId();
        
        return resolutionCache.get(cacheKey, k -> performDiscovery(backendSet));
    }

    private List<URI> performDiscovery(BackendSet backendSet) {
        for (ServiceDiscoveryProvider provider : providers) {
            if (provider.supports(backendSet.getDiscoveryType())) {
                log.debug("Resolving backend set '{}' using provider '{}'", backendSet.getName(), provider.getClass().getSimpleName());
                return provider.discover(backendSet);
            }
        }
        log.warn("No suitable ServiceDiscoveryProvider found for DiscoveryType: {}", backendSet.getDiscoveryType());
        return Collections.emptyList();
    }
    
    /**
     * Pre-emptively updates the cache for a specific backend set.
     * This is intended to be called by a background task to prevent request-time latency.
     * 
     * @param backendSet The backend set to refresh
     */
    public void updateCache(BackendSet backendSet) {
        String cacheKey = backendSet.getName() + ":" + backendSet.getDiscoveryType() + ":" + backendSet.getServiceId();
        List<URI> resolved = performDiscovery(backendSet);
        if (!resolved.isEmpty()) {
            resolutionCache.put(cacheKey, resolved);
        }
    }

    /**
     * Can be called by the ConfigurationRefreshService to force an immediate clear
     * when the routes.yml file changes.
     */
    public void invalidateCache() {
        log.info("Invalidating Service Discovery resolution cache");
        resolutionCache.invalidateAll();
    }
}
