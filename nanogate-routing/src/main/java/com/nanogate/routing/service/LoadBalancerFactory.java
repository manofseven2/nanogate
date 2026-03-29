package com.nanogate.routing.service;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

/**
 * A factory for retrieving the correct LoadBalancer implementation based on a key.
 */
@Component
public class LoadBalancerFactory {

    private final Map<String, LoadBalancer> loadBalancers;

    public LoadBalancerFactory(Map<String, LoadBalancer> loadBalancers) {
        this.loadBalancers = loadBalancers;
    }

    public Optional<LoadBalancer> getLoadBalancer(String name) {
        return Optional.ofNullable(loadBalancers.get(name));
    }
}