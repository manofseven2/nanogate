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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.http.HttpHeaders;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

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
    @Mock
    private HttpResponse<InputStream> mockHttpResponse;

    @InjectMocks
    private RequestOrchestrator requestOrchestrator;

    private Route route;
    private BackendSet backendSet;
    private URI uri1, uri2;

    @BeforeEach
    void setUp() throws Exception {
        route = new Route();
        route.setBackendSet("test-set");
        backendSet = new BackendSet();
        backendSet.setServers(List.of(new URI("http://s1"), new URI("http://s2")));
        uri1 = new URI("http://server1");
        uri2 = new URI("http://server2");

        lenient().when(properties.getBackendSet("test-set")).thenReturn(backendSet);
        lenient().when(loadBalancerFactory.getLoadBalancer(anyString())).thenReturn(Optional.of(loadBalancer));
        lenient().when(request.getAttribute(MetricAttribute.START_TIME_NANOS.name())).thenReturn(System.nanoTime());
        
        lenient().when(mockHttpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
    }

    @Test
    void orchestrate_SuccessfulFirstAttempt() throws Exception {
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(uri1));
        when(requestProxy.prepareRequest(any(), any(), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(mockHttpResponse.statusCode()).thenReturn(200);

        requestOrchestrator.orchestrate(request, response, route);

        verify(requestProxy).prepareRequest(any(), eq(uri1), any(), any(), any());
        verify(response).setStatus(200);
    }

    @Test
    void orchestrate_RetriesOnCallNotPermitted() throws Exception {
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(uri1)).thenReturn(Optional.of(uri2));
        
        CompletableFuture<HttpResponse<InputStream>> failedFuture = CompletableFuture.failedFuture(new CompletionException(mock(CallNotPermittedException.class)));
        when(requestProxy.prepareRequest(any(), eq(uri1), any(), any(), any())).thenReturn(failedFuture);
        when(requestProxy.prepareRequest(any(), eq(uri2), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(mockHttpResponse.statusCode()).thenReturn(200);

        requestOrchestrator.orchestrate(request, response, route);

        verify(healthCheckService).markAsUnhealthy(uri1);
        verify(requestProxy).prepareRequest(any(), eq(uri2), any(), any(), any());
    }

    @Test
    void orchestrate_RetriesOnIOException() throws Exception {
        when(loadBalancer.chooseBackend(backendSet)).thenReturn(Optional.of(uri1)).thenReturn(Optional.of(uri2));

        CompletableFuture<HttpResponse<InputStream>> failedFuture = CompletableFuture.failedFuture(new UncheckedIOException(new IOException()));
        when(requestProxy.prepareRequest(any(), eq(uri1), any(), any(), any())).thenReturn(failedFuture);
        when(requestProxy.prepareRequest(any(), eq(uri2), any(), any(), any())).thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(mockHttpResponse.statusCode()).thenReturn(200);

        requestOrchestrator.orchestrate(request, response, route);

        verify(healthCheckService).markAsUnhealthy(uri1);
        verify(requestProxy).prepareRequest(any(), eq(uri2), any(), any(), any());
    }
}
