package com.nanogate.routing.service;

import com.nanogate.routing.model.Route;

import java.net.URI;
import java.util.Optional;

/**
 * Defines the contract for a load balancing algorithm that selects a backend server.
 * This abstraction enables swapping algorithms like Round-Robin, Least Connections, etc.
 */
public interface LoadBalancer {

    /**
     * Chooses the next target URI from a given route.
     *
     * @param route The matching route containing target URIs.
     * @return An Optional containing the chosen URI, or empty if none are available.
     */
    Optional<URI> chooseBackend(Route route);
}