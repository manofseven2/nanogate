package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.resilience.service.CircuitBreakerProvider;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RequestProxyTest {

    @Mock
    private HttpClientProvider httpClientProvider;
    @Mock
    private CircuitBreakerProvider circuitBreakerProvider;
    @Mock
    private HttpClient mockHttpClient;
    @Mock
    private HttpServletRequest mockRequest;
    @Mock
    private CircuitBreaker mockCircuitBreaker;

    @InjectMocks
    private RequestProxy requestProxy;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;

    @BeforeEach
    void setUp() {
        when(httpClientProvider.getClient(any())).thenReturn(mockHttpClient);
        when(circuitBreakerProvider.getCircuitBreaker(any(), any())).thenReturn(mockCircuitBreaker);
    }

    @Test
    void prepareRequest_buildsAndReturnsFuture() throws Exception {
        // Given
        URI targetUri = new URI("http://localhost:8080");
        Route route = new Route();
        HttpClientProperties clientProperties = new HttpClientProperties();
        ResilienceProperties resilienceProperties = new ResilienceProperties(null, null, null, null, null, null);

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        when(mockRequest.getContextPath()).thenReturn("");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        CompletableFuture<HttpResponse<java.io.InputStream>> future = new CompletableFuture<>();
        when(mockCircuitBreaker.executeCompletionStage(any(Supplier.class))).thenAnswer(invocation -> future);

        // When
        CompletableFuture<HttpResponse<java.io.InputStream>> result = requestProxy.prepareRequest(mockRequest, targetUri, clientProperties, resilienceProperties, route);

        // Then
        assertEquals(future, result);
        verify(mockCircuitBreaker).executeCompletionStage(any());
    }
}
