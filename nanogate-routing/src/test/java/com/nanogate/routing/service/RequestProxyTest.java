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
import java.time.Duration;
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
        basicRoute = new Route(); // A basic route with no transforms

        lenient().when(httpClientProvider.getClient(any())).thenReturn(mockHttpClient);
        lenient().when(circuitBreakerProvider.getCircuitBreaker(any(), any())).thenReturn(mockCircuitBreaker);
        
        lenient().when(mockCircuitBreaker.executeCompletionStage(any(Supplier.class))).thenAnswer(invocation -> {
            Supplier<CompletableFuture<HttpResponse<InputStream>>> supplier = invocation.getArgument(0);
            return supplier.get();
        });
    }

    @Test
    void testProxyRequest_AppliesHeaderTransforms() throws Exception {
        // --- GIVEN ---
        HeaderTransformProperties requestTransforms = new HeaderTransformProperties(Map.of("X-Added", "true"), List.of("x-remove"));
        HeaderTransformProperties responseTransforms = new HeaderTransformProperties(Map.of("Y-Added", "true"), List.of("y-remove"));
        Route routeWithTransforms = new Route();
        routeWithTransforms.setRequestHeaders(requestTransforms);
        routeWithTransforms.setResponseHeaders(responseTransforms);

        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        when(mockRequest.getContextPath()).thenReturn("");
        Enumeration<String> headerNames = Collections.enumeration(List.of("X-Keep", "X-Remove"));
        when(mockRequest.getHeaderNames()).thenReturn(headerNames);
        when(mockRequest.getHeaders("X-Keep")).thenReturn(Collections.enumeration(List.of("keep-value")));

        HttpHeaders responseHeaders = HttpHeaders.of(
                Map.of("Y-Keep", List.of("keep-value"), "Y-Remove", List.of("remove-value")),
                (k, v) -> true
        );
        when(mockHttpResponse.headers()).thenReturn(responseHeaders);
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(mockHttpResponse.body()).thenReturn(new ByteArrayInputStream(new byte[0]));
        when(mockHttpClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));
        
        // Add the missing mock for getOutputStream()
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        // --- WHEN ---
        requestProxy.proxyRequest(mockRequest, mockResponse, targetUri, properties, resilienceProperties, routeWithTransforms);

        // --- THEN ---
        verify(mockHttpClient).sendAsync(httpRequestCaptor.capture(), any());
        HttpRequest sentRequest = httpRequestCaptor.getValue();
        
        assertTrue(sentRequest.headers().firstValue("X-Keep").isPresent(), "Header 'X-Keep' should be present");
        assertFalse(sentRequest.headers().firstValue("X-Remove").isPresent(), "Header 'X-Remove' should have been removed");
        assertTrue(sentRequest.headers().firstValue("X-Added").isPresent(), "Header 'X-Added' should have been added");
        assertEquals("true", sentRequest.headers().firstValue("X-Added").get());

        verify(mockResponse).addHeader("Y-Keep", "keep-value");
        verify(mockResponse, never()).addHeader(eq("Y-Remove"), anyString());
        verify(mockResponse).setHeader("Y-Added", "true");
    }
}
