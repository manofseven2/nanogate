package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InMemoryRouteLocatorTest {

    @Mock
    private NanoGateRouteProperties properties;

    @Mock
    private HttpServletRequest request;

    @InjectMocks
    private InMemoryRouteLocator routeLocator;

    private List<Route> routes;

    @BeforeEach
    void setUp() {
        routes = new ArrayList<>();
        
        Route catchAllRoute = new Route();
        catchAllRoute.setId("catch-all");
        catchAllRoute.setPath("/api/**");
        
        Route specificRoute = new Route();
        specificRoute.setId("specific-users");
        specificRoute.setPath("/api/users/**");
        
        Route exactRoute = new Route();
        exactRoute.setId("exact-user");
        exactRoute.setPath("/api/users/123");

        // Add them in an unsorted order to prove the locator sorts them
        routes.add(catchAllRoute);
        routes.add(exactRoute);
        routes.add(specificRoute);
    }

    @Test
    void testFindRoute_WhenDisabled_ShouldReturnEmpty() {
        when(properties.isEnabled()).thenReturn(false);

        Optional<Route> result = routeLocator.findRoute(request);

        assertTrue(result.isEmpty());
    }

    @Test
    void testFindRoute_WithExactMatch_ShouldReturnExactRoute() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRoutes()).thenReturn(routes);
        when(request.getRequestURI()).thenReturn("/api/users/123");

        Optional<Route> result = routeLocator.findRoute(request);

        assertTrue(result.isPresent());
        assertEquals("exact-user", result.get().getId());
    }

    @Test
    void testFindRoute_WithSpecificMatch_ShouldReturnSpecificRoute() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRoutes()).thenReturn(routes);
        when(request.getRequestURI()).thenReturn("/api/users/456"); // Doesn't match exact, falls back to /users/**

        Optional<Route> result = routeLocator.findRoute(request);

        assertTrue(result.isPresent());
        assertEquals("specific-users", result.get().getId());
    }

    @Test
    void testFindRoute_WithGeneralMatch_ShouldReturnCatchAllRoute() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRoutes()).thenReturn(routes);
        when(request.getRequestURI()).thenReturn("/api/products"); // Doesn't match specific or exact

        Optional<Route> result = routeLocator.findRoute(request);

        assertTrue(result.isPresent());
        assertEquals("catch-all", result.get().getId());
    }

    @Test
    void testFindRoute_WithNoMatch_ShouldReturnEmpty() {
        when(properties.isEnabled()).thenReturn(true);
        when(properties.getRoutes()).thenReturn(routes);
        when(request.getRequestURI()).thenReturn("/auth/login"); // Matches nothing

        Optional<Route> result = routeLocator.findRoute(request);

        assertTrue(result.isEmpty());
    }
}
