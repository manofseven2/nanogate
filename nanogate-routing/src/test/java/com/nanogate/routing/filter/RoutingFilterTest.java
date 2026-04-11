package com.nanogate.routing.filter;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
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
        when(request.getRequestURI()).thenReturn("/api/some-path");
        lenient().when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
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
        when(routeLocator.findRoute(request)).thenReturn(Optional.empty());
        routingFilter.doFilter(request, response, filterChain);
        verify(response).sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_CircuitBreakerOpen_ShouldRetryAndMarkUnhealthy() throws Exception {
        when(routeLocator.findRoute(request)).thenReturn(Optional.of(route));
        when(properties.getBackendSet("backend1")).thenReturn(backendSet);
        when(loadBalancer.chooseBackend(backendSet))
                .thenReturn(Optional.of(targetUri1)) // First attempt fails
                .thenReturn(Optional.of(targetUri2)); // Second attempt succeeds

        // Create a real, open circuit breaker to throw the exception
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.of(CircuitBreakerConfig.ofDefaults());
        CircuitBreaker circuitBreaker = registry.circuitBreaker("test-breaker");
        circuitBreaker.transitionToOpenState();

        // Mock the circuit breaker exception on the first call only
        doThrow(CallNotPermittedException.createCallNotPermittedException(circuitBreaker))
                .when(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri1), any(), any());

        routingFilter.doFilter(request, response, filterChain);

        // Verify that the first server was marked as unhealthy
        verify(healthCheckService).markAsUnhealthy(targetUri1);

        // Verify that the request was successfully proxied to the second server
        verify(requestProxy).proxyRequest(eq(request), eq(response), eq(targetUri2), any(), any());

        // Verify connection tracking was attempted for both
        verify(connectionTracker).increment(targetUri1);
        verify(connectionTracker).decrement(targetUri1);
        verify(connectionTracker).increment(targetUri2);
        verify(connectionTracker).decrement(targetUri2);
    }
    
    // Other tests would go here, simplified for brevity
}
