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

/**
 * An in-memory implementation of LoadBalancer that uses a round-robin strategy.
 * The bean is explicitly named "ROUND_ROBIN" for consistent lookup.
 */
@Component("ROUND_ROBIN")
public class RoundRobinLoadBalancer implements LoadBalancer {

    private static final Logger log = LoggerFactory.getLogger(RoundRobinLoadBalancer.class);

    // This map holds a counter for each BackendSet name. This is an instance-local cache.
    private final ConcurrentHashMap<String, AtomicInteger> backendSetCounters = new ConcurrentHashMap<>();

    @Override
    public Optional<URI> chooseBackend(BackendSet backendSet) {
        List<URI> servers = backendSet.getServers();
        if (servers == null || servers.isEmpty()) {
            return Optional.empty();
        }

        // Get or create a counter for this specific backend set
        AtomicInteger counter = backendSetCounters.computeIfAbsent(backendSet.getName(), k -> new AtomicInteger(0));

        // Atomically get the next index.
        int nextIndex;
        int currentIndex;
        do {
            currentIndex = counter.get();
            nextIndex = (currentIndex + 1) % servers.size();
        } while (!counter.compareAndSet(currentIndex, nextIndex));

        URI selectedUri = servers.get(nextIndex);
        log.debug("RoundRobin selected backend URI: {} for backend set: {}", selectedUri, backendSet.getName());

        return Optional.of(selectedUri);
    }
}