package com.nanogate.routing.service;

import com.nanogate.routing.model.BackendSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * An in-memory implementation of LoadBalancer that uses a round-robin strategy.
 * It only considers servers that are marked as healthy by the HealthCheckService.
 * The bean is explicitly named "ROUND_ROBIN" for consistent lookup.
 */
@Component("ROUND_ROBIN")
public class RoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    private final ConcurrentHashMap<String, AtomicInteger> backendSetCounters = new ConcurrentHashMap<>();
    private final HealthCheckService healthCheckService;

    public RoundRobinLoadBalancer(HealthCheckService healthCheckService) {
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

        // Get or create a counter for this specific backend set
        AtomicInteger counter = backendSetCounters.computeIfAbsent(backendSet.getName(), k -> new AtomicInteger(0));

        // Atomically get the next index based on the size of the *healthy* server list
        int nextIndex = counter.getAndIncrement() % healthyServers.size();

        URI selectedUri = healthyServers.get(nextIndex);
        log.debug("RoundRobin selected healthy backend URI: {} for backend set: {}", selectedUri, backendSet.getName());

        return Optional.of(selectedUri);
    }
}
