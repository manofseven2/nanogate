package com.nanogate.routing.config;

import com.nanogate.routing.model.Route;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Maps the 'nanogate.routing.routes' properties from application.yml to a list of Route objects.
 */
@Component
@ConfigurationProperties(prefix = "nanogate.routing")
public class RoutingProperties {

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