package com.nanogate.routing.service;

import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Tracks the number of active HTTP connections currently being proxied to each backend URI.
 * This state is used by dynamic load balancing algorithms like Least Connections.
 * 
 * In a future phase, this could be backed by Redis for cluster-wide connection tracking.
 */
@Service
public class ActiveConnectionTracker {

    private final ConcurrentHashMap<URI, AtomicInteger> activeConnections = new ConcurrentHashMap<>();

    /**
     * Increments the active connection count for a specific backend URI.
     * @param uri The backend server URI.
     */
    public void increment(URI uri) {
        if (uri == null) return;
        activeConnections.computeIfAbsent(uri, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * Decrements the active connection count for a specific backend URI.
     * Ensures the count never drops below zero.
     * @param uri The backend server URI.
     */
    public void decrement(URI uri) {
        if (uri == null) return;
        AtomicInteger count = activeConnections.get(uri);
        if (count != null) {
            count.updateAndGet(current -> Math.max(0, current - 1));
        }
    }

    /**
     * Gets the current number of active connections for a specific backend URI.
     * @param uri The backend server URI.
     * @return The number of active connections, or 0 if not currently tracked.
     */
    public int getActiveConnections(URI uri) {
        if (uri == null) return 0;
        AtomicInteger count = activeConnections.get(uri);
        return count != null ? count.get() : 0;
    }
}
