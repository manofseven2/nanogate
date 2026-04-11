package com.nanogate.resilience.model;

import java.time.Duration;

/**
 * Configuration properties for circuit breakers.
 * Allows for hierarchical configuration (Global -> BackendSet -> Route).
 *
 * @param failureRateThreshold The failure rate threshold in percentage. If the failure rate is equal to or greater than the threshold, the Circuit Breaker transitions to open and starts short-circuiting calls.
 * @param slowCallRateThreshold The threshold for slow calls in percentage. If the percentage of slow calls is equal to or greater than the threshold, the Circuit Breaker transitions to open.
 * @param slowCallDurationThreshold The duration threshold for a call to be considered slow.
 * @param permittedNumberOfCallsInHalfOpenState The number of permitted calls when the Circuit Breaker is in 'half-open' state.
 * @param slidingWindowSize The size of the sliding window used to record the outcome of calls when the Circuit Breaker is closed.
 * @param waitDurationInOpenState The time that the Circuit Breaker should wait before transitioning from open to half-open.
 */
public record ResilienceProperties(
    Float failureRateThreshold,
    Float slowCallRateThreshold,
    Duration slowCallDurationThreshold,
    Integer permittedNumberOfCallsInHalfOpenState,
    Integer slidingWindowSize,
    Duration waitDurationInOpenState
) {
    public ResilienceProperties merge(ResilienceProperties other) {
        if (other == null) {
            return this;
        }
        return new ResilienceProperties(
            other.failureRateThreshold() != null ? other.failureRateThreshold() : this.failureRateThreshold(),
            other.slowCallRateThreshold() != null ? other.slowCallRateThreshold() : this.slowCallRateThreshold(),
            other.slowCallDurationThreshold() != null ? other.slowCallDurationThreshold() : this.slowCallDurationThreshold(),
            other.permittedNumberOfCallsInHalfOpenState() != null ? other.permittedNumberOfCallsInHalfOpenState() : this.permittedNumberOfCallsInHalfOpenState(),
            other.slidingWindowSize() != null ? other.slidingWindowSize() : this.slidingWindowSize(),
            other.waitDurationInOpenState() != null ? other.waitDurationInOpenState() : this.waitDurationInOpenState()
        );
    }
}
