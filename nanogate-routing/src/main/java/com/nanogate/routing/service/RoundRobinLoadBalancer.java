package com.nanogate.routing.service;

import com.nanogate.routing.model.Route;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory implementation of LoadBalancer that uses a round-robin strategy.
 */
@Service
public class RoundRobinLoadBalancer implements LoadBalancer {

    // Using a map to hold AtomicInteger for each route's ID to maintain state across requests
    // For a real-world scenario, this would need to be distributed (e.g., using Redis)
    // or managed by a dedicated service discovery client.
    private final java.util.Map<String, AtomicInteger> routeCounters = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public Optional<URI> chooseBackend(Route route) {
        List<URI> targetUris = route.getTargetUris();
        if (targetUris == null || targetUris.isEmpty()) {
            return Optional.empty();
        }

        // Get or create a counter for this route
        AtomicInteger counter = routeCounters.computeIfAbsent(route.getId(), k -> new AtomicInteger(0));

        // Get the next index in a thread-safe manner
        int index = counter.getAndIncrement() % targetUris.size();

        // Handle potential negative values if counter overflows (though unlikely with AtomicInteger)
        if (index < 0) {
            index = index + targetUris.size();
        }

        return Optional.of(targetUris.get(index));
    }
}