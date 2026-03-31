package com.nanogate.routing.config;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class NanoGateRoutePropertiesTest {

    private NanoGateRouteProperties properties;

    @BeforeEach
    void setUp() {
        properties = new NanoGateRouteProperties();
    }

    @Test
    void testGetAndSetEnabled() {
        assertTrue(properties.isEnabled());
        properties.setEnabled(false);
        assertFalse(properties.isEnabled());
    }

    @Test
    void testGetAndSetDefaultHttpClient() {
        assertNull(properties.getDefaultHttpClient());
        HttpClientProperties clientProps = new HttpClientProperties();
        properties.setDefaultHttpClient(clientProps);
        assertEquals(clientProps, properties.getDefaultHttpClient());
    }

    @Test
    void testGetAndSetRoutes() {
        assertTrue(properties.getRoutes().isEmpty());
        List<Route> routes = new ArrayList<>();
        routes.add(new Route());
        properties.setRoutes(routes);
        assertEquals(1, properties.getRoutes().size());
    }

    @Test
    void testGetAndSetBackendSets() {
        assertTrue(properties.getBackendSets().isEmpty());
        List<BackendSet> backendSets = new ArrayList<>();
        backendSets.add(new BackendSet());
        properties.setBackendSets(backendSets);
        assertEquals(1, properties.getBackendSets().size());
    }

    @Test
    void testInitializeAndValidate_WithValidConfiguration_ShouldNotThrowException() {
        // Arrange
        BackendSet backendSet = new BackendSet();
        backendSet.setName("backend1");
        properties.setBackendSets(List.of(backendSet));

        Route route = new Route();
        route.setId("route1");
        route.setBackendSet("backend1");
        properties.setRoutes(List.of(route));

        // Act & Assert
        assertDoesNotThrow(() -> properties.initializeAndValidate());
        assertEquals(backendSet, properties.getBackendSet("backend1"));
    }

    @Test
    void testInitializeAndValidate_WithEmptyConfiguration_ShouldNotThrowException() {
        // Act & Assert
        assertDoesNotThrow(() -> properties.initializeAndValidate());
        assertNull(properties.getBackendSet("any"));
    }

    @Test
    void testInitializeAndValidate_WithInvalidConfiguration_ShouldThrowException() {
        // Arrange
        BackendSet backendSet = new BackendSet();
        backendSet.setName("backend1");
        properties.setBackendSets(List.of(backendSet));

        Route route = new Route();
        route.setId("route1");
        route.setBackendSet("non-existent-backend"); // Invalid reference
        properties.setRoutes(List.of(route));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> properties.initializeAndValidate());
        assertTrue(exception.getMessage().contains("non-existent-backend"));
    }
    
    @Test
    void testInitializeAndValidate_WithDuplicateBackendSets_ShouldUseFirstOneAndLogWarning() {
        // Arrange
        BackendSet backendSet1 = new BackendSet();
        backendSet1.setName("duplicate-backend");
        backendSet1.setLoadBalancer("round-robin"); // Differentiate them for testing

        BackendSet backendSet2 = new BackendSet();
        backendSet2.setName("duplicate-backend");
        backendSet2.setLoadBalancer("least-connections");

        properties.setBackendSets(List.of(backendSet1, backendSet2));

        Route route = new Route();
        route.setId("route1");
        route.setBackendSet("duplicate-backend");
        properties.setRoutes(List.of(route));

        // Act & Assert
        assertDoesNotThrow(() -> properties.initializeAndValidate());
        
        // Assert that the first one is the one in the map
        BackendSet retrievedBackend = properties.getBackendSet("duplicate-backend");
        assertNotNull(retrievedBackend);
        assertEquals("round-robin", retrievedBackend.getLoadBalancer());
    }

    @Test
    void testInitializeAndValidate_WithRoutingDisabled_ShouldNotThrowExceptionForInvalidConfig() {
         // Arrange
        properties.setEnabled(false); // Disable routing
        
        BackendSet backendSet = new BackendSet();
        backendSet.setName("backend1");
        properties.setBackendSets(List.of(backendSet));

        Route route = new Route();
        route.setId("route1");
        route.setBackendSet("non-existent-backend"); // Invalid reference
        properties.setRoutes(List.of(route));

        // Act & Assert
        assertDoesNotThrow(() -> properties.initializeAndValidate());
    }
}