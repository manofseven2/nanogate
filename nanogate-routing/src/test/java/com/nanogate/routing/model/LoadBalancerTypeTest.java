package com.nanogate.routing.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTypeTest {

    @Test
    void testEnumValues() {
        LoadBalancerType[] types = LoadBalancerType.values();
        assertEquals(3, types.length);
        
        assertEquals(LoadBalancerType.ROUND_ROBIN, LoadBalancerType.valueOf("ROUND_ROBIN"));
        assertEquals(LoadBalancerType.LEAST_CONNECTIONS, LoadBalancerType.valueOf("LEAST_CONNECTIONS"));
        assertEquals(LoadBalancerType.RANDOM, LoadBalancerType.valueOf("RANDOM"));
    }
}
