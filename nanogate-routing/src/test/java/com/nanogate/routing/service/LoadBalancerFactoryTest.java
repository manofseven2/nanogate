package com.nanogate.routing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ExtendWith(MockitoExtension.class)
class LoadBalancerFactoryTest {

    @Mock
    private LoadBalancer mockRoundRobin;

    @Mock
    private LoadBalancer mockRandom;

    private LoadBalancerFactory factory;

    @BeforeEach
    void setUp() {
        Map<String, LoadBalancer> balancers = new HashMap<>();
        balancers.put("ROUND_ROBIN", mockRoundRobin);
        balancers.put("RANDOM", mockRandom);
        
        factory = new LoadBalancerFactory(balancers);
    }

    @Test
    void testGetLoadBalancer_WithValidName_ShouldReturnBalancer() {
        Optional<LoadBalancer> result = factory.getLoadBalancer("ROUND_ROBIN");
        assertTrue(result.isPresent());
        assertEquals(mockRoundRobin, result.get());
    }

    @Test
    void testGetLoadBalancer_WithInvalidName_ShouldReturnEmpty() {
        Optional<LoadBalancer> result = factory.getLoadBalancer("UNKNOWN_STRATEGY");
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLoadBalancer_WithNullName_ShouldReturnEmpty() {
        Optional<LoadBalancer> result = factory.getLoadBalancer(null);
        assertTrue(result.isEmpty());
    }

    @Test
    void testGetLoadBalancer_WithEmptyName_ShouldReturnEmpty() {
        Optional<LoadBalancer> result = factory.getLoadBalancer("   ");
        assertTrue(result.isEmpty());
    }
}
