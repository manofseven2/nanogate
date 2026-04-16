package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.metrics.MetricAttribute;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.Route;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestOrchestratorTest {

    @Mock
    private NanoGateRouteProperties properties;
    @Mock
    private LoadBalancerFactory loadBalancerFactory;
    @Mock
    private RequestProxy requestProxy;
    @Mock
    private ActiveConnectionTracker connectionTracker;
    @Mock
    private HealthCheckService healthCheckService;
    @Mock
    private LoadBalancer loadBalancer;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private RequestOrchestrator requestOrchestrator;

    private Route route;
    private BackendSet backendSet;
    private URI uri1;
    private URI uri2;

    @BeforeEach
    void setUp() throws Exception {
        route = new Route();
        route.setBackendSet("test-set");
        backendSet = new BackendSet();
        uri1 = new URI("http://server1");
        uri2 = new URI("http://server2");

        lenient().when(properties.getBackendSet("test-set")).thenReturn(backendSet);
        lenient().when(loadBalancerFactory.getLoadBalancer(any())).thenReturn(Optional.of(loadBalancer));
        lenient().when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(uri1));
        lenient().when(request.getAttribute(MetricAttribute.START_TIME_NANOS.name())).thenReturn(System.nanoTime());
    }

    @Test
    void orchestrate_setsOverheadAndBackendDurationAttributes() throws Exception {
        requestOrchestrator.orchestrate(request, response, route);

        verify(request).setAttribute(eq(MetricAttribute.OVERHEAD_DURATION_NANOS.name()), any(Long.class));
        verify(request).setAttribute(eq(MetricAttribute.BACKEND_DURATION_NANOS.name()), any(Long.class));
        verify(connectionTracker).increment(uri1);
        verify(connectionTracker).decrement(uri1);
    }

    @Test
    void orchestrate_BackendSetNotFound_Sends500() throws Exception {
        when(properties.getBackendSet("test-set")).thenReturn(null);

        requestOrchestrator.orchestrate(request, response, route);

        verify(response).sendError(eq(HttpServletResponse.SC_INTERNAL_SERVER_ERROR), anyString());
    }

    @Test
    void orchestrate_NoBackendAvailable_Sends503() throws Exception {
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.empty());

        requestOrchestrator.orchestrate(request, response, route);

        verify(response).sendError(eq(HttpServletResponse.SC_SERVICE_UNAVAILABLE), anyString());
    }

    @Test
    void orchestrate_CircuitBreakerOpen_RetriesAndMarksUnhealthy() throws Exception {
        when(loadBalancer.chooseBackend(backendSet))
                .thenReturn(Optional.of(uri1))
                .thenReturn(Optional.of(uri2));

        doThrow(mock(CallNotPermittedException.class))
                .doNothing()
                .when(requestProxy).proxyRequest(eq(request), eq(response), any(), any(), any(), eq(route));

        requestOrchestrator.orchestrate(request, response, route);

        verify(healthCheckService).markAsUnhealthy(uri1);
        verify(requestProxy).proxyRequest(eq(request), eq(response), eq(uri2), any(), any(), eq(route));
        verify(connectionTracker).increment(uri1);
        verify(connectionTracker).decrement(uri1);
        verify(connectionTracker).increment(uri2);
        verify(connectionTracker).decrement(uri2);
    }

    @Test
    void orchestrate_ProxyThrowsException_Sends502() throws Exception {
        doThrow(new RuntimeException("Network error"))
                .when(requestProxy).proxyRequest(any(), any(), any(), any(), any(), any());

        requestOrchestrator.orchestrate(request, response, route);

        verify(response).sendError(eq(HttpServletResponse.SC_BAD_GATEWAY), anyString());
        verify(connectionTracker).decrement(uri1);
    }
}
