package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RouteLocator;
import com.nanogate.security.SecurityConstants;
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

        routeResolutionFilter.doFilterInternal(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeLocator);
    }

    @Test
    void doFilter_NoRouteMatched_ShouldSend404() throws Exception {
        when(request.getRequestURI()).thenReturn("/api/unknown");
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());
        
        routeResolutionFilter.doFilterInternal(request, response, filterChain);
        
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_RouteMatched_ShouldSetAttributesAndProceed() throws Exception {
        Route route = new Route();
        route.setIpSet("admin-ips");
        when(request.getRequestURI()).thenReturn("/api/known");
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));

        routeResolutionFilter.doFilterInternal(request, response, filterChain);

        verify(request).setAttribute(SecurityConstants.IP_SET_ATTRIBUTE, "admin-ips");
        verify(request).setAttribute("nanogate.matched_route", route);
        verify(filterChain).doFilter(request, response);
        verify(response, never()).sendError(anyInt(), anyString());
    }
}
