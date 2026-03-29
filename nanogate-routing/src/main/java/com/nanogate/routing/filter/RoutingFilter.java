package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.LoadBalancer;
import com.nanogate.routing.service.RouteLocator;
import com.nanogate.routing.service.RequestProxy;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

/**
 * The main routing filter for NanoGate.
 * It intercepts all incoming requests, finds a matching route, selects a backend, and proxies the request.
 */
@Component
@Order(1) // Ensure this filter runs early in the chain
public class RoutingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RoutingFilter.class);

    private final RouteLocator routeLocator;
    private final LoadBalancer loadBalancer;
    private final RequestProxy requestProxy;

    public RoutingFilter(RouteLocator routeLocator, LoadBalancer loadBalancer, RequestProxy requestProxy) {
        this.routeLocator = routeLocator;
        this.loadBalancer = loadBalancer;
        this.requestProxy = requestProxy;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        log.debug("Intercepting request: {} {}", request.getMethod(), request.getRequestURI());

        Optional<Route> optionalRoute = routeLocator.findRoute(request);

        if (optionalRoute.isPresent()) {
            Route route = optionalRoute.get();
            log.debug("Route matched: {} -> {}", route.getPath(), route.getTargetUris());

            Optional<URI> optionalTargetUri = loadBalancer.chooseBackend(route);

            if (optionalTargetUri.isPresent()) {
                URI targetUri = optionalTargetUri.get();
                log.info("Proxying request to backend: {} {}", request.getRequestURI(), targetUri);
                try {
                    requestProxy.proxyRequest(request, response, targetUri);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.error("Request proxying interrupted for {} to {}: {}", request.getRequestURI(), targetUri, e.getMessage());
                    response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Gateway interrupted while proxying request.");
                } catch (Exception e) {
                    log.error("Error proxying request for {} to {}: {}", request.getRequestURI(), targetUri, e.getMessage());
                    response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
                }
            } else {
                log.warn("No backend available for route: {}", route.getId());
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
            }
        } else {
            log.warn("No route matched for request: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No route found for the requested path.");
        }
    }
}