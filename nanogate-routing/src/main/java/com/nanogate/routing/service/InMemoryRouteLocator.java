package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Optional;

/**
 * An in-memory implementation of RouteLocator that finds routes based on configuration properties.
 */
@Service
public class InMemoryRouteLocator implements RouteLocator {

    private final NanoGateRouteProperties properties;
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    public InMemoryRouteLocator(NanoGateRouteProperties properties) {
        this.properties = properties;
    }

    @Override
    public Optional<Route> findRoute(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return Optional.empty();
        }

        String requestPath = request.getRequestURI();
        return properties.getRoutes().stream()
                .filter(route -> pathMatcher.match(route.getPath(), requestPath))
                .findFirst();
    }
}