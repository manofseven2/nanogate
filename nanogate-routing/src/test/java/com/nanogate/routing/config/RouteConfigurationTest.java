package com.nanogate.routing.config;

import com.nanogate.routing.model.Route;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RouteConfigurationTest {

    @Test
    void testRouteConfigurationRecord() {
        // Arrange
        Route route = new Route();
        route.setId("route1");
        List<Route> routes = List.of(route);

        // Act
        RouteConfiguration config = new RouteConfiguration(true, routes);

        // Assert
        assertTrue(config.enabled(), "Enabled should be true");
        assertEquals(1, config.routes().size(), "Routes list should have 1 element");
        assertEquals("route1", config.routes().get(0).getId(), "Route ID should match");
    }
}