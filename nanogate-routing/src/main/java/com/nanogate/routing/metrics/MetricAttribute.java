package com.nanogate.routing.metrics;

/**
 * Defines type-safe keys for request attributes used to pass metrics data
 * between different filters and services within the gateway.
 */
public enum MetricAttribute {
    /**
     * The time at which request processing started, captured using System.nanoTime().
     * Stored as a Long.
     */
    START_TIME_NANOS,

    /**
     * The duration in nanoseconds of the gateway's internal processing (overhead)
     * before the request is sent to the backend.
     * Stored as a Long.
     */
    OVERHEAD_DURATION_NANOS,

    /**
     * The duration in nanoseconds of the backend service's response time, including
     * network latency.
     * Stored as a Long.
     */
    BACKEND_DURATION_NANOS;
}
