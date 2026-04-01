package com.nanogate.routing.service;

import com.nanogate.routing.model.BackendSet;

import java.net.URI;
import java.util.Optional;

/**
 * Defines the contract for a load balancing algorithm that selects a backend server from a BackendSet.
 * This abstraction enables swapping algorithms like Round-Robin, Least Connections, etc.
 */
public interface LoadBalancer {

    /**
     * Chooses the next target URI from a given BackendSet.
     *
     * @param backendSet The BackendSet containing the list of servers.
     * @return An Optional containing the chosen URI, or empty if none are available.
     */
    Optional<URI> chooseBackend(BackendSet backendSet);
}