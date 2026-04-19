package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.metrics.MetricAttribute;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HeaderTransformProperties;
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
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.List;
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
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No healthy backend instances available.");
            return;
        }

        URI targetUri = optionalTargetUri.get();
        HttpClientProperties clientProperties = resolveHttpClientProperties(route, backendSet);
        ResilienceProperties resilienceProperties = resolveResilienceProperties(route, backendSet);

        long backendStartTime = System.nanoTime();
        
        connectionTracker.increment(targetUri);
        try {
            HttpResponse<InputStream> backendResponse = requestProxy.prepareRequest(request, targetUri, clientProperties, resilienceProperties, route).join();
            writeResponseToClient(response, backendResponse, route.getResponseHeaders());
        } catch (Exception e) {
            Throwable rootCause = findRootCause(e);
            if (rootCause instanceof CallNotPermittedException) {
                log.warn("Circuit for {} is open. Retrying on a different backend.", targetUri);
                healthCheckService.markAsUnhealthy(targetUri);
                proxyWithRetry(request, response, route, backendSet);
            } else if (rootCause instanceof IOException) {
                log.warn("Request to {} failed with IO exception. Retrying on a different backend.", targetUri, rootCause);
                healthCheckService.markAsUnhealthy(targetUri);
                proxyWithRetry(request, response, route, backendSet);
            } else {
                handleUnexpectedException(response, targetUri, e);
            }
        } finally {
            connectionTracker.decrement(targetUri);
            long backendDuration = System.nanoTime() - backendStartTime;
            request.setAttribute(MetricAttribute.BACKEND_DURATION_NANOS.name(), backendDuration);
        }
    }

    private Throwable findRootCause(Throwable throwable) {
        Throwable rootCause = throwable;
        while (rootCause.getCause() != null && rootCause.getCause() != rootCause) {
            rootCause = rootCause.getCause();
        }
        return rootCause;
    }

    private void writeResponseToClient(HttpServletResponse response, HttpResponse<InputStream> backendResponse, HeaderTransformProperties transforms) throws IOException {
        response.setStatus(backendResponse.statusCode());
        List<String> toRemove = (transforms != null && transforms.remove() != null)
                ? transforms.remove().stream().map(String::toLowerCase).toList()
                : Collections.emptyList();

        if (backendResponse.headers() != null) {
            backendResponse.headers().map().forEach((name, values) -> {
                if (toRemove.contains(name.toLowerCase())) {
                    log.debug("Removing response header: {}", name);
                    return;
                }
                if (!isHopByHopHeader(name)) {
                    values.forEach(value -> response.addHeader(name, value));
                }
            });
        }

        if (transforms != null && transforms.add() != null) {
            transforms.add().forEach((key, value) -> {
                log.debug("Adding/overwriting response header: {}={}", key, value);
                response.setHeader(key, value);
            });
        }

        try (InputStream bodyStream = backendResponse.body()) {
            if (bodyStream != null) {
                bodyStream.transferTo(response.getOutputStream());
                response.getOutputStream().flush();
            }
        }
    }

    private void handleUnexpectedException(HttpServletResponse response, URI targetUri, Exception e) throws IOException {
        log.error("An unexpected, non-retryable error occurred during proxying to {}", targetUri, e);
        if (!response.isCommitted()) {
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "An unexpected gateway error occurred.");
        }
    }

    private boolean isHopByHopHeader(String headerName) {
        return List.of("connection", "keep-alive", "proxy-authenticate", "proxy-authorization", "te", "trailers", "transfer-encoding", "upgrade").contains(headerName.toLowerCase());
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
