package com.nanogate.routing.service;

import com.nanogate.routing.model.BackendSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A load balancer that routes requests to the backend server with the fewest active connections.
 * It only considers servers that are marked as healthy by the HealthCheckService.
 * It queries the ActiveConnectionTracker to determine the current load on each server.
 */
@Component("LEAST_CONNECTIONS")
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(LeastConnectionsLoadBalancer.class);

    private final ActiveConnectionTracker connectionTracker;
    private final HealthCheckService healthCheckService;

    public LeastConnectionsLoadBalancer(ActiveConnectionTracker connectionTracker, HealthCheckService healthCheckService) {
        this.connectionTracker = connectionTracker;
        this.healthCheckService = healthCheckService;
    }

    @Override
    public Optional<URI> chooseBackend(BackendSet backendSet) {
        List<URI> servers = backendSet.getServers();
        if (servers == null || servers.isEmpty()) {
            return Optional.empty();
        }

        // Filter out unhealthy servers BEFORE attempting to load balance
        List<URI> healthyServers = servers.stream()
                .filter(healthCheckService::isHealthy)
                .collect(Collectors.toList());

        if (healthyServers.isEmpty()) {
            log.warn("No healthy servers available for backend set: {}", backendSet.getName());
            return Optional.empty();
        }

        URI leastBusyServer = null;
        int lowestCount = Integer.MAX_VALUE;

        for (URI server : healthyServers) {
            int currentCount = connectionTracker.getActiveConnections(server);
            
            if (currentCount < lowestCount) {
                lowestCount = currentCount;
                leastBusyServer = server;
            }
            
            // Optimization: If a server has 0 active connections, pick it immediately.
            if (lowestCount == 0) {
                break;
            }
        }

        log.debug("LeastConnections selected healthy backend {} with {} active connections for backend set: {}",
                  leastBusyServer, lowestCount, backendSet.getName());

        return Optional.ofNullable(leastBusyServer);
    }
}
