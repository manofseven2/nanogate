package com.nanogate.routing.config;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.CorsProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NanoGateCorsConfigurationSourceTest {

    private NanoGateRouteProperties routeProperties;
    private NanoGateCorsConfigurationSource source;
    private HttpServletRequest request;

    @BeforeEach
    void setUp() {
        routeProperties = mock(NanoGateRouteProperties.class);
        source = new NanoGateCorsConfigurationSource(routeProperties);
        request = mock(HttpServletRequest.class);
    }

    @Test
    void getCorsConfiguration_returnsNullIfNoRouteAndNoGlobal() {
        when(request.getAttribute("NANO_ROUTE")).thenReturn(null);
        when(routeProperties.getDefaultCors()).thenReturn(null);

        assertNull(source.getCorsConfiguration(request));
    }

    @Test
    void getCorsConfiguration_returnsNullIfDisabled() {
        Route route = new Route();
        CorsProperties cors = new CorsProperties();
        cors.setEnabled(false);
        route.setCors(cors);
        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);

        assertNull(source.getCorsConfiguration(request));
    }

    @Test
    void getCorsConfiguration_resolvesFromRoute() {
        Route route = new Route();
        CorsProperties cors = new CorsProperties();
        cors.setEnabled(true);
        cors.setAllowedOrigins(List.of("http://example.com"));
        route.setCors(cors);

        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);

        CorsConfiguration config = source.getCorsConfiguration(request);
        
        assertNotNull(config);
        assertTrue(config.getAllowedOrigins().contains("http://example.com"));
    }

    @Test
    void getCorsConfiguration_cascadesToBackendSet() {
        Route route = new Route();
        route.setBackendSet("backend1");
        
        BackendSet backendSet = new BackendSet();
        CorsProperties cors = new CorsProperties();
        cors.setEnabled(true);
        cors.setAllowedMethods(List.of("GET", "POST"));
        backendSet.setCors(cors);

        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);
        when(routeProperties.getBackendSet("backend1")).thenReturn(backendSet);

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertTrue(config.getAllowedMethods().contains("GET"));
    }

    @Test
    void getCorsConfiguration_cascadesToGlobal() {
        Route route = new Route();
        route.setBackendSet("backend1");
        
        BackendSet backendSet = new BackendSet(); // no cors
        
        CorsProperties globalCors = new CorsProperties();
        globalCors.setEnabled(true);
        globalCors.setAllowCredentials(true);
        
        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);
        when(routeProperties.getBackendSet("backend1")).thenReturn(backendSet);
        when(routeProperties.getDefaultCors()).thenReturn(globalCors);

        CorsConfiguration config = source.getCorsConfiguration(request);

        assertNotNull(config);
        assertTrue(config.getAllowCredentials());
    }
}
