package com.nanogate.routing.service;

import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;

import java.util.Optional;

/**
 * Defines the contract for locating a route that matches an incoming request.
 * This abstraction allows for different strategies of finding routes (e.g., from memory, a database, or a service registry).
 */
public interface RouteLocator {

    /**
     * Finds the first route that matches the given HTTP request.
     *
     * @param request The incoming HTTP request.
     * @return An Optional containing the matched Route, or an empty Optional if no route matches.
     */
    Optional<Route> findRoute(HttpServletRequest request);
}