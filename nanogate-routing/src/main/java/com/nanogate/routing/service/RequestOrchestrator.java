package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.metrics.MetricAttribute;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.util.Optional;

@Service
public class RequestOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(RequestOrchestrator.class);

    private final NanoGateRouteProperties properties;
    private final LoadBalancerFactory loadBalancerFactory;
    private final RequestProxy requestProxy;
    private final ActiveConnectionTracker connectionTracker;
    private final HealthCheckService healthCheckService;

    public RequestOrchestrator(NanoGateRouteProperties properties, LoadBalancerFactory loadBalancerFactory,
                               RequestProxy requestProxy, ActiveConnectionTracker connectionTracker,
                               HealthCheckService healthCheckService) {
        this.properties = properties;
        this.loadBalancerFactory = loadBalancerFactory;
        this.requestProxy = requestProxy;
        this.connectionTracker = connectionTracker;
        this.healthCheckService = healthCheckService;
    }

    public void orchestrate(HttpServletRequest request, HttpServletResponse response, Route route) throws IOException {
        Long startTime = (Long) request.getAttribute(MetricAttribute.START_TIME_NANOS.name());
        
        BackendSet backendSet = properties.getBackendSet(route.getBackendSet());
        if (backendSet == null) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway configuration error.");
            return;
        }

        if (startTime != null) {
            long overhead = System.nanoTime() - startTime;
            request.setAttribute(MetricAttribute.OVERHEAD_DURATION_NANOS.name(), overhead);
        }

        proxyWithRetry(request, response, route, backendSet);
    }

    private void proxyWithRetry(HttpServletRequest request, HttpServletResponse response, Route route, BackendSet backendSet) throws IOException {
        String strategyName = StringUtils.hasText(route.getLoadBalancer())
                ? route.getLoadBalancer()
                : backendSet.getLoadBalancer();

        Optional<URI> optionalTargetUri = loadBalancerFactory.getLoadBalancer(strategyName)
                .flatMap(lb -> lb.chooseBackend(backendSet));

        if (optionalTargetUri.isEmpty()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
            return;
        }

        URI targetUri = optionalTargetUri.get();
        HttpClientProperties clientProperties = resolveHttpClientProperties(route, backendSet);
        ResilienceProperties resilienceProperties = resolveResilienceProperties(route, backendSet);

        long backendStartTime = System.nanoTime();
        
        connectionTracker.increment(targetUri);
        try {
            requestProxy.proxyRequest(request, response, targetUri, clientProperties, resilienceProperties, route);
        } catch (CallNotPermittedException e) {
            log.warn("Circuit for {} is open. Marking as unhealthy and attempting to find another backend.", targetUri);
            healthCheckService.markAsUnhealthy(targetUri);
            proxyWithRetry(request, response, route, backendSet); // Recursive retry
        } catch (Exception e) {
            response.sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
        } finally {
            connectionTracker.decrement(targetUri);
            long backendDuration = System.nanoTime() - backendStartTime;
            request.setAttribute(MetricAttribute.BACKEND_DURATION_NANOS.name(), backendDuration);
        }
    }

    private HttpClientProperties resolveHttpClientProperties(Route route, BackendSet backendSet) {
        HttpClientProperties globalProps = properties.getDefaultHttpClient() != null ? properties.getDefaultHttpClient() : new HttpClientProperties();
        return globalProps.merge(backendSet.getHttpClient()).merge(route.getHttpClient());
    }

    private ResilienceProperties resolveResilienceProperties(Route route, BackendSet backendSet) {
        ResilienceProperties globalProps = properties.getDefaultResilience() != null ? properties.getDefaultResilience() : new ResilienceProperties(null, null, null, null, null, null);
        return globalProps.merge(backendSet.getResilience()).merge(route.getResilience());
    }
}
