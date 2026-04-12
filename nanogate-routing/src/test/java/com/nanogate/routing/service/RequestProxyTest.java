package com.nanogate.routing.service;

import com.nanogate.resilience.model.ResilienceProperties;
import com.nanogate.resilience.service.CircuitBreakerProvider;
import com.nanogate.routing.model.HeaderTransformProperties;
import com.nanogate.routing.model.HttpClientProperties;
import com.nanogate.routing.model.Route;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

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
    private HttpServletResponse mockResponse;

    @Mock
    private HttpResponse<InputStream> mockHttpResponse;

    @Mock
    private ServletOutputStream mockOutputStream;
    
    @Mock
    private CircuitBreaker mockCircuitBreaker;

    @InjectMocks
    private RequestProxy requestProxy;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;

    private URI targetUri;
    private HttpClientProperties properties;
    private ResilienceProperties resilienceProperties;
    private Route basicRoute;

    @BeforeEach
    void setUp() throws Exception {
        targetUri = new URI("http://backend-service:8080");
        properties = new HttpClientProperties();
        resilienceProperties = new ResilienceProperties(null, null, null, null, null, null);
        basicRoute = new Route();

        lenient().when(httpClientProvider.getClient(any())).thenReturn(mockHttpClient);
        lenient().when(circuitBreakerProvider.getCircuitBreaker(any(), any())).thenReturn(mockCircuitBreaker);
        
        lenient().when(mockCircuitBreaker.executeCompletionStage(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<CompletableFuture<HttpResponse<InputStream>>> supplier = invocation.getArgument(0);
            return supplier.get();
        });

        // Common mocks for all tests
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockHttpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);
    }

    @Test
    void testProxyRequest_AppliesUrlAndHeaderRewriting() throws Exception {
        // --- GIVEN ---
        Route route = new Route();
        route.setStripPrefix(1); // strip /api
        route.setRewritePath("/users/(?<id>.*)");
        route.setRewriteReplacement("/user/fetch?id=${id}");
        route.setRequestHeaders(new HeaderTransformProperties(Map.of("X-Trace-Id", "nanogate-{header.X-Request-Id}"), null));

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/api/users/123");
        when(mockRequest.getContextPath()).thenReturn("");
        when(mockRequest.getHeader("X-Request-Id")).thenReturn("abc-123");
        when(mockRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration()); // Prevent NPE

        // --- WHEN ---
        requestProxy.proxyRequest(mockRequest, mockResponse, targetUri, properties, resilienceProperties, route);

        // --- THEN ---
        verify(mockHttpClient).sendAsync(httpRequestCaptor.capture(), any());
        HttpRequest sentRequest = httpRequestCaptor.getValue();

        // Verify URL was rewritten correctly
        assertEquals("http://backend-service:8080/user/fetch?id=123", sentRequest.uri().toString());

        // Verify header was derived correctly
        assertTrue(sentRequest.headers().firstValue("X-Trace-Id").isPresent());
        assertEquals("nanogate-abc-123", sentRequest.headers().firstValue("X-Trace-Id").get());
    }
}
