package com.nanogate.routing.security;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.config.RouteRegistry;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.Route;
import com.nanogate.security.model.GlobalSecuritySettings;
import com.nanogate.security.model.SecurityProperties;
import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DefaultRouteSecurityResolverTest {

    private DefaultRouteSecurityResolver resolver;
    private RouteRegistry routeRegistry;
    private NanoGateRouteProperties props;

    @BeforeEach
    void setUp() {
        routeRegistry = mock(RouteRegistry.class);
        props = new NanoGateRouteProperties();
        when(routeRegistry.get()).thenReturn(props);
        resolver = new DefaultRouteSecurityResolver(mock(com.nanogate.routing.service.InMemoryRouteLocator.class), routeRegistry);
    }

    @Test
    void shouldInheritGlobalEnabled() {
        GlobalSecuritySettings global = props.getSecurity();
        global.setEnabled(true);

        HttpServletRequest request = mock(HttpServletRequest.class);
        // No route found in request attribute
        
        RouteSecurityResolver.ResolvedSecurityPolicy policy = resolver.resolvePolicy(request);
        assertTrue(policy.enabled());
    }

    @Test
    void shouldOverrideGlobalWithBackendSet() {
        props.getSecurity().setEnabled(true);
        
        BackendSet bs = new BackendSet();
        bs.setName("my-bs");
        SecurityProperties bsSec = new SecurityProperties();
        bsSec.setEnabled(false);
        bs.setSecurity(bsSec);
        props.getBackendSets().add(bs);
        props.initializeAndValidate();

        Route route = new Route();
        route.setBackendSet("my-bs");
        
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("nanogate.matched_route")).thenReturn(route);

        RouteSecurityResolver.ResolvedSecurityPolicy policy = resolver.resolvePolicy(request);
        assertFalse(policy.enabled());
    }

    @Test
    void shouldOverrideEverythingWithRouteLevel() {
        props.getSecurity().setEnabled(false);

        Route route = new Route();
        SecurityProperties rSec = new SecurityProperties();
        rSec.setEnabled(true);
        rSec.setRequiredRoles(List.of("admin"));
        route.setSecurity(rSec);

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("nanogate.matched_route")).thenReturn(route);

        RouteSecurityResolver.ResolvedSecurityPolicy policy = resolver.resolvePolicy(request);
        assertTrue(policy.enabled());
        assertEquals(List.of("admin"), policy.requiredRoles());
    }

    @Test
    void shouldMergeForwardClaimsFromAllLevels() {
        props.getSecurity().setForwardClaims(Map.of("global-claim", "X-Global"));
        
        BackendSet bs = new BackendSet();
        bs.setName("my-bs");
        SecurityProperties bsSec = new SecurityProperties();
        bsSec.setForwardClaims(Map.of("bs-claim", "X-BS", "global-claim", "X-Overridden"));
        bs.setSecurity(bsSec);
        props.getBackendSets().add(bs);
        props.initializeAndValidate();

        Route route = new Route();
        route.setBackendSet("my-bs");
        route.setSecurity(new SecurityProperties());
        route.getSecurity().setForwardClaims(Map.of("route-claim", "X-Route"));

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getAttribute("nanogate.matched_route")).thenReturn(route);

        RouteSecurityResolver.ResolvedSecurityPolicy policy = resolver.resolvePolicy(request);
        Map<String, String> claims = policy.forwardClaims();
        assertEquals("X-Overridden", claims.get("global-claim"));
        assertEquals("X-BS", claims.get("bs-claim"));
        assertEquals("X-Route", claims.get("route-claim"));
    }
}
