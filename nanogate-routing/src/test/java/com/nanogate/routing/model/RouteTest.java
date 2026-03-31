package com.nanogate.routing.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class RouteTest {

    @Test
    void testGetAndSetProperties() {
        Route route = new Route();
        
        route.setId("route-1");
        assertEquals("route-1", route.getId());

        route.setPath("/api/**");
        assertEquals("/api/**", route.getPath());

        route.setBackendSet("backend-1");
        assertEquals("backend-1", route.getBackendSet());

        route.setLoadBalancer("least-connections");
        assertEquals("least-connections", route.getLoadBalancer());

        HttpClientProperties properties = new HttpClientProperties();
        route.setHttpClient(properties);
        assertEquals(properties, route.getHttpClient());
    }
}
