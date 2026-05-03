package com.nanogate.routing.config;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.CacheProperties;
import com.nanogate.routing.model.CorsProperties;
import com.nanogate.routing.model.HealthCheckProperties;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.RateLimitProperties;
import com.nanogate.routing.model.Route;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration properties for NanoGate.
 * Binds properties under the 'nanogate.routing' prefix and validates their consistency upon startup.
 */
@Configuration
@ConfigurationProperties(prefix = "nanogate.routing")
public class NanoGateRouteProperties {

    private static final Logger log = LoggerFactory.getLogger(NanoGateRouteProperties.class);

    private boolean enabled = true;
    private HttpClientProperties defaultHttpClient;
    private ResilienceProperties defaultResilience;
    private HealthCheckProperties defaultHealthCheck;
    private RateLimitProperties defaultRateLimit;
    private CorsProperties defaultCors;
    private List<Route> routes = new ArrayList<>();
    private List<BackendSet> backendSets = new ArrayList<>();

    // This map is initialized after properties are loaded to provide efficient lookups.
    private Map<String, BackendSet> backendSetMap = Collections.emptyMap();

    /**
     * This method is automatically called after Spring has finished populating the properties.
     * It validates that all routes point to existing backend-sets and warns about unmonitored sets.
     *
     * @throws IllegalStateException if a route references a non-existent backend-set.
     */
    @PostConstruct
    public void initializeAndValidate() {
        // It's valid to have no backend sets, especially if there are no routes.
        if (!CollectionUtils.isEmpty(backendSets)) {
            this.backendSetMap = backendSets.stream()
                    .collect(Collectors.toMap(
                            BackendSet::getName,
                            Function.identity(),
                            (existing, replacement) -> {
                                log.warn("Duplicate backend-set name found: '{}'. The first definition will be used.", existing.getName());
                                return existing;
                            }
                    ));
            
            // Log warnings for unmonitored backend sets
            for (BackendSet backendSet : backendSets) {
                if (backendSet.getHealthCheck() == null && defaultHealthCheck == null) {
                    log.warn("BackendSet '{}' does not have a health-check configured and no default is available. Servers in this set will be assumed healthy and will not be actively monitored.", backendSet.getName());
                }
            }
        }

        // If routing is enabled, validate the routes.
        if (enabled && !CollectionUtils.isEmpty(routes)) {
            for (Route route : routes) {
                if (!backendSetMap.containsKey(route.getBackendSet())) {
                    throw new IllegalStateException(
                            String.format("Configuration validation error: Route with ID '%s' and path '%s' refers to a non-existent backend-set: '%s'",
                                    route.getId(), route.getPath(), route.getBackendSet())
                    );
                }
            }
        }
        log.info("NanoGate configuration loaded and validated. Found {} routes and {} backend-sets.",
                routes.size(), backendSets.size());
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public HttpClientProperties getDefaultHttpClient() {
        return defaultHttpClient;
    }

    public void setDefaultHttpClient(HttpClientProperties defaultHttpClient) {
        this.defaultHttpClient = defaultHttpClient;
    }

    public ResilienceProperties getDefaultResilience() {
        return defaultResilience;
    }

    public void setDefaultResilience(ResilienceProperties defaultResilience) {
        this.defaultResilience = defaultResilience;
    }

    public HealthCheckProperties getDefaultHealthCheck() {
        return defaultHealthCheck;
    }

    public void setDefaultHealthCheck(HealthCheckProperties defaultHealthCheck) {
        this.defaultHealthCheck = defaultHealthCheck;
    }

    public RateLimitProperties getDefaultRateLimit() {
        return defaultRateLimit;
    }

    public void setDefaultRateLimit(RateLimitProperties defaultRateLimit) {
        this.defaultRateLimit = defaultRateLimit;
    }

    public CorsProperties getDefaultCors() {
        return defaultCors;
    }

    public void setDefaultCors(CorsProperties defaultCors) {
        this.defaultCors = defaultCors;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }

    public List<BackendSet> getBackendSets() {
        return backendSets;
    }

    public void setBackendSets(List<BackendSet> backendSets) {
        this.backendSets = backendSets;
    }

    /**
     * Retrieves a BackendSet by its name using the pre-initialized lookup map.
     *
     * @param name The name of the backend-set.
     * @return The corresponding BackendSet, or null if not found.
     */
    public BackendSet getBackendSet(String name) {
        return backendSetMap.get(name);
    }
}