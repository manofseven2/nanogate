package com.nanogate.routing.filter;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.*;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
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
    private HealthCheckService healthCheckService;

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
    private URI targetUri1;
    private URI targetUri2;

    @BeforeEach
    void setUp() throws Exception {
        route = new Route();
        route.setId("route1");
        route.setBackendSet("backend1");

        backendSet = new BackendSet();
        backendSet.setName("backend1");

        targetUri1 = new URI("http://localhost:8081");
        targetUri2 = new URI("http://localhost:8082");

        ReflectionTestUtils.setField(routingFilter, "actuatorBasePath", "/actuator");
        
        // --- Default Mocks for the "Happy Path" ---
        // Most tests assume a route is found and a load balancer exists.
        // Tests that need a different behavior will override these mocks.
        lenient().when(request.getRequestURI()).thenReturn("/api/some-path");
        lenient().when(routeLocator.findRoute(any())).thenReturn(Optional.of(route));
        lenient().when(properties.getBackendSet(anyString())).thenReturn(backendSet);
        lenient().when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        lenient().when(loadBalancer.chooseBackend(any())).thenReturn(Optional.of(targetUri1));
    }

    @Test
    void doFilter_ActuatorPath_ShouldDelegateToFilterChain() throws Exception {
        when(request.getRequestURI()).thenReturn("/actuator/health");
        routingFilter.doFilter(request, response, filterChain);
        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(routeLocator, requestProxy, connectionTracker);
    }

    @Test
    void doFilter_NoRouteMatched_ShouldSend404() throws Exception {
        // Override the default mock for this specific test
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());
        
        routingFilter.doFilter(request, response, filterChain);
        
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_BackendSetNotFound_ShouldSend500() throws Exception {
        when(properties.getBackendSet("backend1")).thenReturn(null);
        routingFilter.doFilter(request, response, filterChain);
        verify(response).sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Gateway configuration error.");
    }

    @Test
    void doFilter_NoBackendAvailable_ShouldSend503() throws Exception {
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.empty());
        routingFilter.doFilter(request, response, filterChain);
        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "No backend instances available for this service.");
    }

    @Test
    void doFilter_CircuitBreakerOpen_ShouldRetryAndMarkUnhealthy() throws Exception {
        when(loadBalancer.chooseBackend(backendSet))
                .thenReturn(Optional.of(targetUri1)) // First attempt fails
                .thenReturn(Optional.of(targetUri2)); // Second attempt succeeds

        CircuitBreaker circuitBreaker = CircuitBreaker.of("test", CircuitBreakerConfig.ofDefaults());
        circuitBreaker.transitionToOpenState();

        doThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
                .when(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri1), any(), any(), any());

        routingFilter.doFilter(request, response, filterChain);

        verify(healthCheckService).markAsUnhealthy(targetUri1);
        verify(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri2), any(), any(), any());
    }

    @Test
    void doFilter_ProxyThrowsInterruptedException_ShouldSend503() throws Exception {
        doThrow(new InterruptedException("Test interruption"))
                .when(requestProxy).proxyRequest(any(), any(), any(), any(), any(), any());

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Gateway interrupted while proxying request.");
    }

    @Test
    void doFilter_ProxyThrowsGenericException_ShouldSend502() throws Exception {
        doThrow(new RuntimeException("Test generic exception"))
                .when(requestProxy).proxyRequest(any(), any(), any(), any(), any(), any());

        routingFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_BAD_GATEWAY, "Error proxying request to backend.");
    }

    @Test
    void doFilter_SuccessfulProxy_ShouldCallProxyAndTrack() throws Exception {
        routingFilter.doFilter(request, response, filterChain);

        verify(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri1), any(HttpClientProperties.class), any(ResilienceProperties.class), eq(route));
        verify(response, never()).sendError(anyInt(), anyString());
        verify(connectionTracker).increment(targetUri1);
        verify(connectionTracker).decrement(targetUri1);
    }
}
