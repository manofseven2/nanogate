package com.nanogate.routing.filter;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
@Order(1) // Run this filter early, but after Spring's internal filters for actuator
public class RoutingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RoutingFilter.class);

    private final NanoGateRouteProperties properties;
    private final RouteLocator routeLocator;
    private final LoadBalancerFactory loadBalancerFactory;
    private final RequestProxy requestProxy;
    private final ActiveConnectionTracker connectionTracker;
    private final HealthCheckService healthCheckService;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String actuatorBasePath;

    public RoutingFilter(NanoGateRouteProperties properties,
                         RouteLocator routeLocator,
                         LoadBalancerFactory loadBalancerFactory,
                         RequestProxy requestProxy,
                         ActiveConnectionTracker connectionTracker,
                         HealthCheckService healthCheckService) {
        this.properties = properties;
        this.routeLocator = routeLocator;
        this.loadBalancerFactory = loadBalancerFactory;
        this.requestProxy = requestProxy;
        this.connectionTracker = connectionTracker;
        this.healthCheckService = healthCheckService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (request.getRequestURI().startsWith(actuatorBasePath)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        Optional<Route> optionalRoute = routeLocator.findRoute(request);

        if (optionalRoute.isEmpty()) {
            log.warn("No route matched for request: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
            return;
        }

        Route route = optionalRoute.get();
        BackendSet backendSet = properties.getBackendSet(route.getBackendSet());

        if (backendSet == null) {
            log.error("Configuration error: Route '{}' refers to a non-existent backend-set '{}'", route.getId(), route.getBackendSet());
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway configuration error.");
            return;
        }

        HttpClientProperties clientProperties = resolveHttpClientProperties(route, backendSet);
        ResilienceProperties resilienceProperties = resolveResilienceProperties(route, backendSet);

        String strategyName = StringUtils.hasText(route.getLoadBalancer())
                ? route.getLoadBalancer()
                : backendSet.getLoadBalancer();

        Optional<URI> optionalTargetUri = loadBalancerFactory.getLoadBalancer(strategyName)
                .flatMap(lb -> lb.chooseBackend(backendSet));

        if (optionalTargetUri.isEmpty()) {
            log.warn("No backend available for backend-set: {}", backendSet.getName());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
            return;
        }

        URI targetUri = optionalTargetUri.get();
        proxyWithRetry(request, response, targetUri, clientProperties, resilienceProperties, route, backendSet, strategyName);
    }

    private void proxyWithRetry(HttpServletRequest request, HttpServletResponse response, URI targetUri,
                                HttpClientProperties clientProperties, ResilienceProperties resilienceProperties,
                                Route route, BackendSet backendSet, String strategyName) throws IOException {
        
        connectionTracker.increment(targetUri);
        try {
            requestProxy.proxyRequest(request, response, targetUri, clientProperties, resilienceProperties, route);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit for {} is open. Marking as unhealthy and attempting to find another backend.", targetUri);
            healthCheckService.markAsUnhealthy(targetUri);

            // Retry logic
            Optional<URI> nextTargetUri = loadBalancerFactory.getLoadBalancer(strategyName)
                    .flatMap(lb -> lb.chooseBackend(backendSet));

            if (nextTargetUri.isPresent()) {
                proxyWithRetry(request, response, nextTargetUri.get(), clientProperties, resilienceProperties, route, backendSet, strategyName);
            } else {
                log.error("All backends for set '{}' are unavailable after circuit break.", backendSet.getName());
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "All backend instances are currently unavailable.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Request proxying interrupted for backend {}: {}", targetUri, e.getMessage());
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Gateway interrupted while proxying request.");
        } catch (Exception e) {
            log.error("Error proxying request to backend {}: {}", targetUri, e.getMessage());
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
        } finally {
            connectionTracker.decrement(targetUri);
        }
    }

    private HttpClientProperties resolveHttpClientProperties(Route route, BackendSet backendSet) {
        HttpClientProperties globalProps = properties.getDefaultHttpClient() != null
                ? properties.getDefaultHttpClient()
                : new HttpClientProperties();
        return globalProps.merge(backendSet.getHttpClient()).merge(route.getHttpClient());
    }

    private ResilienceProperties resolveResilienceProperties(Route route, BackendSet backendSet) {
        ResilienceProperties globalProps = properties.getDefaultResilience() != null
                ? properties.getDefaultResilience()
                : new ResilienceProperties(null, null, null, null, null, null);
        return globalProps.merge(backendSet.getResilience()).merge(route.getResilience());
    }
}
