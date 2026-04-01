package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import java.util.Comparator;
import java.util.Optional;

/**
 * An in-memory implementation of RouteLocator that finds routes based on configuration properties.
 * It correctly prioritizes more specific routes.
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

        // Get the comparator from AntPathMatcher which understands path specificity
        Comparator<Route> routeComparator = (r1, r2) ->
                pathMatcher.getPatternComparator(requestPath).compare(r1.getPath(), r2.getPath());

        // Sort the routes to ensure the most specific path is matched first,
        // then find the first one that matches the request.
        return properties.getRoutes().stream()
                .filter(route -> pathMatcher.match(route.getPath(), requestPath))
                .min(routeComparator); // .min() with the comparator gives us the "best" or most specific match
    }
}