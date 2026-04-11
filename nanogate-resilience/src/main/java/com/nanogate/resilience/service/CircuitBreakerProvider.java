package com.nanogate.resilience.service;

import com.nanogate.resilience.model.ResilienceProperties;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CircuitBreakerProvider {

    private final ConcurrentHashMap<String, CircuitBreaker> circuitBreakerCache = new ConcurrentHashMap<>();
    private final CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();

    public CircuitBreaker getCircuitBreaker(URI serverUri, ResilienceProperties properties) {
        // Create a unique name for the circuit breaker based on the server URI
        String breakerName = serverUri.toString();
        
        // Return the cached circuit breaker if it exists, otherwise create a new one
        return circuitBreakerCache.computeIfAbsent(breakerName, name -> {
            CircuitBreakerConfig config = buildCircuitBreakerConfig(properties);
            return registry.circuitBreaker(name, config);
        });
    }

    private CircuitBreakerConfig buildCircuitBreakerConfig(ResilienceProperties props) {
        CircuitBreakerConfig.Builder builder = CircuitBreakerConfig.custom();

        if (props.failureRateThreshold() != null) {
            builder.failureRateThreshold(props.failureRateThreshold());
        }
        if (props.slowCallRateThreshold() != null) {
            builder.slowCallRateThreshold(props.slowCallRateThreshold());
        }
        if (props.slowCallDurationThreshold() != null) {
            builder.slowCallDurationThreshold(props.slowCallDurationThreshold());
        }
        if (props.permittedNumberOfCallsInHalfOpenState() != null) {
            builder.permittedNumberOfCallsInHalfOpenState(props.permittedNumberOfCallsInHalfOpenState());
        }
        if (props.slidingWindowSize() != null) {
            builder.slidingWindowSize(props.slidingWindowSize());
        }
        if (props.waitDurationInOpenState() != null) {
            builder.waitDurationInOpenState(props.waitDurationInOpenState());
        }

        return builder.build();
    }
}
