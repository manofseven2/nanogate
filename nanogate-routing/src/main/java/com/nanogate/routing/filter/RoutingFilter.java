package com.nanogate.routing.filter;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.LoadBalancerFactory;
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
import org.springframework.util.StringUtils;

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

    private final NanoGateRouteProperties properties;
    private final RouteLocator routeLocator;
    private final LoadBalancerFactory loadBalancerFactory;
    private final RequestProxy requestProxy;

    public RoutingFilter(NanoGateRouteProperties properties, RouteLocator routeLocator, LoadBalancerFactory loadBalancerFactory, RequestProxy requestProxy) {
        this.properties = properties;
        this.routeLocator = routeLocator;
        this.loadBalancerFactory = loadBalancerFactory;
        this.requestProxy = requestProxy;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        Optional<Route> optionalRoute = routeLocator.findRoute(request);

        if (optionalRoute.isEmpty()) {
            log.warn("No route matched for request: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "No route found for the requested path.");
            return;
        }

        Route route = optionalRoute.get();
        BackendSet backendSet = properties.getBackendSet(route.getBackendSet());

        if (backendSet == null) {
            log.error("Configuration error: Route '{}' refers to a non-existent backend-set '{}'", route.getId(), route.getBackendSet());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway configuration error.");
            return;
        }

        // 1. Determine the final HttpClient properties using the hierarchy
        HttpClientProperties clientProperties = resolveHttpClientProperties(route, backendSet);

        // 2. Determine the load balancer strategy
        String strategyName = StringUtils.hasText(route.getLoadBalancer())
                ? route.getLoadBalancer()
                : backendSet.getLoadBalancer();

        // 3. Choose a backend server
        Optional<URI> optionalTargetUri = loadBalancerFactory.getLoadBalancer(strategyName)
                .flatMap(lb -> lb.chooseBackend(backendSet));

        if (optionalTargetUri.isEmpty()) {
            log.warn("No backend available for backend-set: {}", backendSet.getName());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
            return;
        }

        // 4. Proxy the request
        URI targetUri = optionalTargetUri.get();
        log.info("Proxying request for route '{}' to backend: {}", route.getId(), targetUri);

        try {
            requestProxy.proxyRequest(request, response, targetUri, clientProperties);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Request proxying interrupted for route '{}' to {}: {}", route.getId(), targetUri, e.getMessage());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Gateway interrupted while proxying request.");
        } catch (Exception e) {
            log.error("Error proxying request for route '{}' to {}: {}", route.getId(), targetUri, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
        }
    }

    private HttpClientProperties resolveHttpClientProperties(Route route, BackendSet backendSet) {
        HttpClientProperties globalProps = properties.getDefaultHttpClient() != null
                ? properties.getDefaultHttpClient()
                : new HttpClientProperties();

        HttpClientProperties backendProps = backendSet.getHttpClient();
        HttpClientProperties routeProps = route.getHttpClient();

        // Merge properties: Global -> BackendSet -> Route
        return globalProps.merge(backendProps).merge(routeProps);
    }
}