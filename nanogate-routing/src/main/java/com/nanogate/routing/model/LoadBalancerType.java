package com.nanogate.routing.model;

/**
 * Enum representing the supported load balancing strategies.
 */
public enum LoadBalancerType {
    /**
     * Distributes requests sequentially across the list of backend servers.
     */
    ROUND_ROBIN,
    /**
     * Sends requests to the server with the fewest active connections (to be implemented).
     */
    LEAST_CONNECTIONS,
    /**
     * Selects a server at random (to be implemented).
     */
    RANDOM
}