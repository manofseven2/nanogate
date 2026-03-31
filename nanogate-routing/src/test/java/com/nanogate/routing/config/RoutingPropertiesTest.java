package com.nanogate.routing.config;

import com.nanogate.routing.model.Route;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RoutingPropertiesTest {

    @Test
    void testGetAndSetEnabled() {
        RoutingProperties properties = new RoutingProperties();
        assertTrue(properties.isEnabled(), "Default should be true");

        properties.setEnabled(false);
        assertFalse(properties.isEnabled(), "Should update to false");
    }

    @Test
    void testGetAndSetRoutes() {
        RoutingProperties properties = new RoutingProperties();
        assertNotNull(properties.getRoutes(), "Default list should not be null");
        assertTrue(properties.getRoutes().isEmpty(), "Default list should be empty");

        List<Route> routes = new ArrayList<>();
        Route route = new Route();
        route.setId("test-route");
        routes.add(route);

        properties.setRoutes(routes);

        assertEquals(1, properties.getRoutes().size(), "Should have 1 route");
        assertEquals("test-route", properties.getRoutes().get(0).getId(), "Route ID should match");
    }
}