package com.nanogate.routing.config;

import com.nanogate.routing.model.Route;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration properties for NanoGate routes.
 * Binds properties under the 'nanogate.routing' prefix from application.yml.
 */
@Configuration
@ConfigurationProperties(prefix = "nanogate.routing")
public class NanoGateRouteProperties {

    private boolean enabled = true;
    private List<Route> routes = new ArrayList<>();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public List<Route> getRoutes() {
        return routes;
    }

    public void setRoutes(List<Route> routes) {
        this.routes = routes;
    }
}