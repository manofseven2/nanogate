package com.nanogate.routing.config;

import com.nanogate.routing.model.Route;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Maps the `nanogate.routing` properties from application.yml into a type-safe configuration object.
 */
@ConfigurationProperties(prefix = "nanogate.routing")
public record RouteConfiguration(
        boolean enabled,
        List<Route> routes
) {
}