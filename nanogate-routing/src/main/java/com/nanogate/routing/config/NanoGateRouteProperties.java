package com.nanogate.routing.config;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Configuration properties for NanoGate.
 * Binds properties under the 'nanogate.routing' prefix from application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "nanogate.routing")
public class NanoGateRouteProperties {

    private boolean enabled = true;
    private HttpClientProperties defaultHttpClient;
    private List<Route> routes = new ArrayList<>();
    private List<BackendSet> backendSets = new ArrayList<>();
    private Map<String, BackendSet> backendSetMap;

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
        // Create a map for efficient lookup
        this.backendSetMap = backendSets.stream()
                .collect(Collectors.toMap(BackendSet::getName, Function.identity()));
    }

    public BackendSet getBackendSet(String name) {
        if (backendSetMap == null && backendSets != null) {
            this.backendSetMap = backendSets.stream()
                    .collect(Collectors.toMap(BackendSet::getName, Function.identity(), (existing, replacement) -> existing));
        }
        return (backendSetMap != null) ? backendSetMap.get(name) : null;
    }
}