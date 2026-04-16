package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RequestOrchestrator;
import com.nanogate.routing.service.RouteLocator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingFilterTest {

    @Mock
    private RouteLocator routeLocator;
    @Mock
    private RequestOrchestrator requestOrchestrator;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RoutingFilter routingFilter;

    @BeforeEach
    void setUp() {
        // Manually inject the @Value field for the test
        ReflectionTestUtils.setField(routingFilter, "actuatorBasePath", "/actuator");
    }

    @Test
    void doFilter_ActuatorPath_ShouldDelegateToFilterChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        routingFilter.doFilter(request, response, filterChain);

        // Verify it passes the request down the chain
        verify(filterChain).doFilter(request, response);
        // Verify it does NOT attempt to do any routing
        verifyNoInteractions(routeLocator, requestOrchestrator);
    }

    @Test
    void doFilter_NoRouteMatched_ShouldSend404() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/unknown");
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());
        
        routingFilter.doFilter(request, response, filterChain);
        
        // Verify it sends a 404 error
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        // Verify it does NOT pass the request down the chain or orchestrate
        verify(filterChain, never()).doFilter(request, response);
        verifyNoInteractions(requestOrchestrator);
    }

    @Test
    void doFilter_RouteMatched_ShouldDelegateToOrchestrator() throws Exception {
        Route route = new Route();
        when(request.getRequestURI()).thenReturn("/api/known");
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));

        routingFilter.doFilter(request, response, filterChain);

        // Verify it delegates the work to the orchestrator
        verify(requestOrchestrator).orchestrate(request, response, route);
        // Verify it does NOT pass the request down the chain or send an error
        verify(filterChain, never()).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
}
