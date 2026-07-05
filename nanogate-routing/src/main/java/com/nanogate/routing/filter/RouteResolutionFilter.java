package com.nanogate.routing.filter;

import net.logstash.logback.argument.StructuredArguments;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RouteLocator;
import com.nanogate.security.SecurityConstants;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
@Order(-200) // Run before Security to match the route only once
public class RouteResolutionFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RouteResolutionFilter.class);

    private final RouteLocator routeLocator;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String actuatorBasePath;

    public RouteResolutionFilter(RouteLocator routeLocator) {
        this.routeLocator = routeLocator;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws IOException, ServletException {

        if (request.getRequestURI().startsWith(actuatorBasePath)) {
            filterChain.doFilter(request, response);
            return;
        }

        Optional<Route> matchedRoute = routeLocator.findRoute(request);
        
        // Store for downstream filters (Security, RateLimit, etc.) to avoid re-matching
        matchedRoute.ifPresent(route -> request.setAttribute("nanogate.matched_route", route));

        if (matchedRoute.isEmpty()) {
            log.warn("No route matched for request: {} {}", request.getMethod(), request.getRequestURI(),
                    StructuredArguments.kv("method", request.getMethod()),
                    StructuredArguments.kv("uri", request.getRequestURI()),
                    StructuredArguments.kv("error_type", "RouteNotFound"));
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
            return;
        }

        Route route = matchedRoute.get();
        if (route.getIpSet() != null) {
            request.setAttribute(SecurityConstants.IP_SET_ATTRIBUTE, route.getIpSet());
        }
        
        // We can pass the whole route object, but since other filters are decoupled,
        // we pass the necessary identifiers or we can just pass the whole route.
        request.setAttribute("NANO_ROUTE", route);

        filterChain.doFilter(request, response);
    }
}
