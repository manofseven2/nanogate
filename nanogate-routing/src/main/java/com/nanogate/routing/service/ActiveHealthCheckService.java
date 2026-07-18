package com.nanogate.routing.service;

import net.logstash.logback.argument.StructuredArguments;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import jakarta.annotation.PostConstruct;

import com.nanogate.routing.config.ConfigurationRefreshedEvent;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.config.RouteRegistry;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HealthCheckProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URISyntaxException;
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

    private final RouteRegistry routeRegistry;
    private final ConcurrentHashMap<URI, AtomicBoolean> healthStatusMap = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Instant> lastCheckTimeMap = new ConcurrentHashMap<>();
    private final HttpClient healthCheckClient;
    private final MeterRegistry meterRegistry;

    // This CompletableFuture will be completed when all checks in the last runHealthChecks cycle are done.
    private volatile CompletableFuture<Void> lastRunCompletion = CompletableFuture.completedFuture(null);

    public ActiveHealthCheckService(RouteRegistry routeRegistry,
                                    @Qualifier("healthCheckHttpClient") HttpClient healthCheckClient,
                                    MeterRegistry meterRegistry) {
        this.routeRegistry = routeRegistry;
        this.healthCheckClient = healthCheckClient;
        this.meterRegistry = meterRegistry;
    }

    @PostConstruct
    public void initGauges() {
        NanoGateRouteProperties properties = routeRegistry.get();
        if (properties != null && properties.getBackendSets() != null) {
            for (BackendSet backendSet : properties.getBackendSets()) {
                if (backendSet.getServers() != null) {
                    for (URI serverUri : backendSet.getServers()) {
                        registerGauge(backendSet.getName(), serverUri);
                    }
                }
            }
        }
    }

    private void registerGauge(String backendSetName, URI serverUri) {
        meterRegistry.gauge("nanogate.backend.health",
            List.of(
                Tag.of("backend_set", backendSetName),
                Tag.of("server", serverUri.toString())
            ),
            serverUri,
            uri -> isHealthy(uri) ? 1.0 : 0.0
        );
    }

    @Scheduled(fixedDelayString = "${nanogate.routing.health-check.ticker-interval:1000}") // Fast, global ticker
    public void runHealthChecks() {
        NanoGateRouteProperties properties = routeRegistry.get();
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
                        if (response.statusCode() >= 200 && response.statusCode() < 400) {
                            markAsHealthy(serverUri);
                        } else {
                            log.warn("Health check failed for {}: Status {}", serverUri, response.statusCode(),
                                    StructuredArguments.kv("backendUri", serverUri),
                                    StructuredArguments.kv("statusCode", response.statusCode()),
                                    StructuredArguments.kv("error_type", "HealthCheckFailed"));
                            markAsUnhealthy(serverUri);
                        }
                    }).exceptionally(throwable -> {
                        log.warn("Health check failed for {}: {}", serverUri, throwable.getMessage(),
                                StructuredArguments.kv("backendUri", serverUri),
                                StructuredArguments.kv("error_type", "HealthCheckFailed"));
                        markAsUnhealthy(serverUri);
                        return null; // Handle exception and complete normally
                    });
        } catch (URISyntaxException e) {
            log.error("Error creating health check URI for {}", serverUri, e,
                    StructuredArguments.kv("backendUri", serverUri),
                    StructuredArguments.kv("error_type", "InvalidHealthCheckUri"));
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
            log.warn("Backend server {} is now marked as DOWN", serverUri,
                    StructuredArguments.kv("backendUri", serverUri),
                    StructuredArguments.kv("event", "BackendMarkedDown"));
        }
    }

    public CompletableFuture<Void> getLastRunCompletion() {
        return lastRunCompletion;
    }

    @EventListener
    public void onConfigurationRefreshed(ConfigurationRefreshedEvent event) {
        log.info("Configuration refreshed. Clearing old health check state.");
        healthStatusMap.clear();
        lastCheckTimeMap.clear();
        initGauges();
    }
}
