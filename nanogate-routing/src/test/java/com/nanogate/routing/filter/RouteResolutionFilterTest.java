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
class RouteResolutionFilterTest {

    @Mock
    private RouteLocator routeLocator;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RouteResolutionFilter routeResolutionFilter;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(routeResolutionFilter, "actuatorBasePath", "/actuator");
    }

    @Test
    void doFilter_ActuatorPath_ShouldDelegateToFilterChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");

        routeResolutionFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeLocator);
    }

    @Test
    void doFilter_NoRouteMatched_ShouldSend404() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/unknown");
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());
        
        routeResolutionFilter.doFilter(request, response, filterChain);
        
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_RouteMatched_ShouldSetAttributesAndProceed() throws Exception {
        Route route = new Route();
        route.setIpSet("test-ip-set");
        when(request.getRequestURI()).thenReturn("/api/known");
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));

        routeResolutionFilter.doFilter(request, response, filterChain);

        verify(request).setAttribute(com.nanogate.security.SecurityConstants.IP_SET_ATTRIBUTE, "test-ip-set");
        verify(request).setAttribute("NANO_ROUTE", route);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
}
