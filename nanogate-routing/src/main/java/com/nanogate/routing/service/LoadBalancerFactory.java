package com.nanogate.routing.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

/**
 * A factory for retrieving a specific LoadBalancer implementation based on a strategy name.
 * This class uses the Factory Pattern to decouple the routing filter from concrete load balancing algorithms.
 */
@Service
public class LoadBalancerFactory {

    private final Map<String, LoadBalancer> loadBalancers;

    /**
     * Injects all beans that implement the LoadBalancer interface into a map,
     * where the key is the bean's name (e.g., "ROUND_ROBIN").
     *
     * @param loadBalancers A map of all LoadBalancer implementations, keyed by their bean name.
     */
    public LoadBalancerFactory(Map<String, LoadBalancer> loadBalancers) {
        this.loadBalancers = loadBalancers;
    }

    /**
     * Retrieves a LoadBalancer instance based on the provided strategy name.
     *
     * @param strategyName The name of the load balancing strategy (e.g., "ROUND_ROBIN").
     * @return An Optional containing the LoadBalancer implementation, or empty if no match is found.
     */
    public Optional<LoadBalancer> getLoadBalancer(String strategyName) {
        if (strategyName == null || strategyName.trim().isEmpty()) {
            return Optional.empty();
        }
        // The lookup is now case-sensitive and must match the bean name exactly.
        return Optional.ofNullable(loadBalancers.get(strategyName));
    }
}