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
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class ActiveHealthCheckService implements HealthCheckService {

    private static final Logger log = LoggerFactory.getLogger(ActiveHealthCheckService.class);

    private final NanoGateRouteProperties properties;
    private final ConcurrentHashMap<URI, AtomicBoolean> healthStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastCheckTimeMap = new ConcurrentHashMap<>();
    private final HttpClient healthCheckClient;

    // This CompletableFuture will be completed when all checks in the last runHealthChecks cycle are done.
    private volatile CompletableFuture<Void> lastRunCompletion = CompletableFuture.completedFuture(null);

    public ActiveHealthCheckService(NanoGateRouteProperties properties,
                                    @Qualifier("healthCheckHttpClient") HttpClient healthCheckClient) {
        this.properties = properties;
        this.healthCheckClient = healthCheckClient;
    }

    @Scheduled(fixedDelayString = "${nanogate.routing.health-check.ticker-interval:1000}") // Fast, global ticker
    public void runHealthChecks() {
        if (!properties.isEnabled()) {
            return;
        }
        log.trace("Health check ticker running...");

        Instant now = Instant.now();
        List<CompletableFuture<Void>> currentChecks = new ArrayList<>();

        for (BackendSet backendSet : properties.getBackendSets()) {
            HealthCheckProperties healthCheckProps = backendSet.getHealthCheck() != null
                    ? backendSet.getHealthCheck()
                    : properties.getDefaultHealthCheck();

            if (healthCheckProps != null && healthCheckProps.path() != null) {
                
                Instant lastCheck = lastCheckTimeMap.getOrDefault(backendSet.getName(), Instant.MIN);
                Duration interval = healthCheckProps.interval() != null ? healthCheckProps.interval() : Duration.ofSeconds(10);

                if (now.isAfter(lastCheck.plus(interval))) {
                    log.debug("Health check interval for backend set '{}' has elapsed. Pinging servers.", backendSet.getName());
                    lastCheckTimeMap.put(backendSet.getName(), now);
                    for (URI serverUri : backendSet.getServers()) {
                        currentChecks.add(checkServerHealth(serverUri, healthCheckProps));
                    }
                }
            }
        }
        // Update the completion future for this run
        this.lastRunCompletion = CompletableFuture.allOf(currentChecks.toArray(new CompletableFuture[0]));
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

    @Override
    public void markAsUnhealthy(URI serverUri) {
        AtomicBoolean previousStatus = healthStatusMap.computeIfAbsent(serverUri, k -> new AtomicBoolean(true));
        if (previousStatus.getAndSet(false)) {
            log.warn("Backend server {} is now marked as DOWN", serverUri);
        }
    }

    public CompletableFuture<Void> getLastRunCompletion() {
        return lastRunCompletion;
    }
}
