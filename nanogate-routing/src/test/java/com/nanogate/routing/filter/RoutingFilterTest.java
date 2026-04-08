package com.nanogate.routing.filter;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.ActiveConnectionTracker;
import com.nanogate.routing.service.LoadBalancer;
import com.nanogate.routing.service.LoadBalancerFactory;
import com.nanogate.routing.service.RequestProxy;
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

import java.net.URI;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RoutingFilterTest {

    @Mock
    private NanoGateRouteProperties properties;
    @Mock
    private RouteLocator routeLocator;
    @Mock
    private LoadBalancerFactory loadBalancerFactory;
    @Mock
    private RequestProxy requestProxy;
    @Mock
    private ActiveConnectionTracker connectionTracker;

    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;
    @Mock
    private LoadBalancer loadBalancer;

    @InjectMocks
    private RoutingFilter routingFilter;

    private Route route;
    private BackendSet backendSet;
    private URI targetUri;

    @BeforeEach
    void setUp() throws Exception {
        route = new Route();
        route.setId("route1");
        route.setBackendSet("backend1");

        backendSet = new BackendSet();
        backendSet.setName("backend1");

        targetUri = new URI("http://localhost:8080");

        // Set the actuatorBasePath field using reflection, as it's @Value injected
        ReflectionTestUtils.setField(routingFilter, "actuatorBasePath", "/actuator");

        // Provide a default stub for getRequestURI to prevent NullPointerExceptions
        when(request.getRequestURI()).thenReturn("/api/some-path");
    }

    @Test
    void doFilter_ActuatorPath_ShouldDelegateToFilterChain() throws Exception {
        // Specific stub for this test to ensure we hit the actuator path
        when(request.getRequestURI()).thenReturn("/actuator/health");

        routingFilter.doFilter(request, response, filterChain);

        // Verify it delegates and does nothing else
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeLocator, requestProxy, connectionTracker);
    }

    @Test
    void doFilter_NoRouteMatched_ShouldSend404() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());

        routingFilter.doFilter(request, response, filterChain);

        // Verify it sends a 404 and does not delegate
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        verify(filterChain, never()).doFilter(request, response);
        verifyNoInteractions(requestProxy, connectionTracker);
    }

    @Test
    void doFilter_BackendSetNotFound_ShouldSend500() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(null);

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway configuration error.");
        verifyNoInteractions(requestProxy, connectionTracker);
    }

    @Test
    void doFilter_NoBackendAvailable_ShouldSend503() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(backendSet);
        when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.empty());

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
        verifyNoInteractions(requestProxy, connectionTracker);
    }

    @Test
    void doFilter_ProxyThrowsInterruptedException_ShouldSend503AndDecrementTracker() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(backendSet);
        when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(targetUri));
        
        doThrow(new InterruptedException("Test interruption")).when(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri), any(HttpClientProperties.class));

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Gateway interrupted while proxying request.");
        
        verify(connectionTracker).increment(targetUri);
        verify(connectionTracker).decrement(targetUri);
    }

    @Test
    void doFilter_ProxyThrowsGenericException_ShouldSend502AndDecrementTracker() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(backendSet);
        when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(targetUri));
        
        doThrow(new RuntimeException("Test generic exception")).when(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri), any(HttpClientProperties.class));

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
        
        verify(connectionTracker).increment(targetUri);
        verify(connectionTracker).decrement(targetUri);
    }

    @Test
    void doFilter_SuccessfulProxy_ShouldNotSendErrorAndTrackConnections() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(backendSet);
        when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(targetUri));

        routingFilter.doFilter(request, response, filterChain);

        verify(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri), any(HttpClientProperties.class));
        verify(response, never()).sendError(anyInt(), anyString());
        
        verify(connectionTracker).increment(targetUri);
        verify(connectionTracker).decrement(targetUri);
    }
}
