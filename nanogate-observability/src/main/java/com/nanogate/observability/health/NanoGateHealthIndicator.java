package com.nanogate.observability.health;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.config.RouteRegistry;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.service.HealthCheckService;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

@Component("nanogate")
public class NanoGateHealthIndicator implements HealthIndicator {

    private final RouteRegistry routeRegistry;
    private final HealthCheckService healthCheckService;

    public NanoGateHealthIndicator(RouteRegistry routeRegistry, HealthCheckService healthCheckService) {
        this.routeRegistry = routeRegistry;
        this.healthCheckService = healthCheckService;
    }

    @Override
    public Health health() {
        Health.Builder builder = Health.up();
        boolean isGatewayOverallHealthy = true;
        NanoGateRouteProperties properties = routeRegistry.get();

        for (BackendSet backendSet : properties.getBackendSets()) {
            Map<String, Object> backendSetDetails = new LinkedHashMap<>();
            Map<String, String> serverDetails = new LinkedHashMap<>();
            boolean isBackendSetHealthy = false;
            int healthyServers = 0;

            if (backendSet.getServers() == null || backendSet.getServers().isEmpty()) {
                continue; // Skip backend sets with no servers
            }

            for (URI serverUri : backendSet.getServers()) {
                if (backendSet.getHealthCheck() != null) {
                    if (healthCheckService.isHealthy(serverUri)) {
                        serverDetails.put(serverUri.toString(), "UP");
                        healthyServers++;
                    } else {
                        serverDetails.put(serverUri.toString(), "DOWN");
                    }
                } else {
                    serverDetails.put(serverUri.toString(), "UNMONITORED");
                    isBackendSetHealthy = true; 
                }
            }

            if (healthyServers > 0) {
                isBackendSetHealthy = true;
            }

            backendSetDetails.put("status", isBackendSetHealthy ? "UP" : "DOWN");
            backendSetDetails.put("servers", serverDetails);
            builder.withDetail(backendSet.getName(), backendSetDetails);

            if (!isBackendSetHealthy && backendSet.getHealthCheck() != null) {
                isGatewayOverallHealthy = false;
            }
        }

        if (!isGatewayOverallHealthy) {
            builder.down();
        }

        return builder.build();
    }
}
