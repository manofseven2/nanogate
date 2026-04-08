package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HealthCheckProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ActiveHealthCheckService implements HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(ActiveHealthCheckService.class);

    private final NanoGateRouteProperties properties;
    private final ConcurrentHashMap<URI, AtomicBoolean> healthStatusMap = new ConcurrentHashMap<>();
    private final HttpClient healthCheckClient;

    public ActiveHealthCheckService(NanoGateRouteProperties properties, 
                                    @Qualifier("healthCheckHttpClient") HttpClient healthCheckClient) {
        this.properties = properties;
        this.healthCheckClient = healthCheckClient;
    }

    @Scheduled(fixedDelayString = "${nanogate.routing.health-check.default-interval:10000}")
    public void runHealthChecks() {
        if (!properties.isEnabled()) {
            return;
        }
        log.debug("Running active health checks for all configured backend sets...");

        for (BackendSet backendSet : properties.getBackendSets()) {
            HealthCheckProperties healthCheckProps = backendSet.getHealthCheck();
            if (healthCheckProps != null && healthCheckProps.path() != null) {
                for (URI serverUri : backendSet.getServers()) {
                    checkServerHealth(serverUri, healthCheckProps);
                }
            }
        }
    }

    public CompletableFuture<Void> checkServerHealth(URI serverUri, HealthCheckProperties healthCheckProps) {
        log.debug("Pinging health check endpoint for server: {}", serverUri);
        try {
            URI healthCheckUri = new URI(serverUri.getScheme(), null, serverUri.getHost(), serverUri.getPort(), healthCheckProps.path(), null, null);
            
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(healthCheckUri)
                    .timeout(healthCheckProps.timeout() != null ? healthCheckProps.timeout() : Duration.ofSeconds(5))
                    .GET()
                    .build();

            return healthCheckClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        if (response.statusCode() >= 200 && response.statusCode() < 300) {
                            markAsHealthy(serverUri);
                        } else {
                            markAsUnhealthy(serverUri);
                            log.warn("Health check failed for {}: Status {}", serverUri, response.statusCode());
                        }
                    }).exceptionally(throwable -> {
                        markAsUnhealthy(serverUri);
                        log.warn("Health check failed for {}: {}", serverUri, throwable.getMessage());
                        return null; // Handle exception and complete normally
                    });
        } catch (Exception e) {
            markAsUnhealthy(serverUri);
            log.error("Error creating health check URI for {}", serverUri, e);
            return CompletableFuture.completedFuture(null);
        }
    }

    @Override
    public boolean isHealthy(URI serverUri) {
        AtomicBoolean status = healthStatusMap.get(serverUri);
        return status == null || status.get();
    }

    private void markAsHealthy(URI serverUri) {
        AtomicBoolean previousStatus = healthStatusMap.computeIfAbsent(serverUri, k -> new AtomicBoolean(false));
        if (!previousStatus.getAndSet(true)) {
            log.info("Backend server {} is now marked as UP", serverUri);
        }
    }

    private void markAsUnhealthy(URI serverUri) {
        AtomicBoolean previousStatus = healthStatusMap.computeIfAbsent(serverUri, k -> new AtomicBoolean(true));
        if (previousStatus.getAndSet(false)) {
            log.warn("Backend server {} is now marked as DOWN", serverUri);
        }
    }
}
