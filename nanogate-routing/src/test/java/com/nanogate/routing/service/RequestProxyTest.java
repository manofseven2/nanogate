package com.nanogate.routing.service;

import com.nanogate.routing.model.HttpClientProperties;
import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RequestProxyTest {

    @Mock
    private HttpClientProvider httpClientProvider;

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

    @InjectMocks
    private RequestProxy requestProxy;

    @Captor
    private ArgumentCaptor<HttpRequest> httpRequestCaptor;

    private URI targetUri;
    private HttpClientProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        targetUri = new URI("http://backend-service:8080");
        properties = new HttpClientProperties();
        
        // This is necessary for both tests, so it stays in setUp
        lenient().when(httpClientProvider.getClient(properties)).thenReturn(mockHttpClient);
    }

    @Test
    void testProxyRequest_BasicFlow() throws Exception {
        // Mock incoming Servlet Request
        when(mockRequest.getMethod()).thenReturn("POST");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        when(mockRequest.getContextPath()).thenReturn("");
        when(mockRequest.getQueryString()).thenReturn("param=value");

        // Mock Headers (one normal, one hop-by-hop)
        Enumeration<String> headerNames = Collections.enumeration(List.of("Content-Type", "Connection"));
        when(mockRequest.getHeaderNames()).thenReturn(headerNames);
        when(mockRequest.getHeaders("Content-Type")).thenReturn(Collections.enumeration(List.of("application/json")));


        // Mock outgoing HTTP Response
        when(mockHttpResponse.statusCode()).thenReturn(201);
        InputStream responseStream = new ByteArrayInputStream("response-body".getBytes());
        when(mockHttpResponse.body()).thenReturn(responseStream);
        HttpHeaders responseHeaders = HttpHeaders.of(
                Map.of("Custom-Header", List.of("custom-value"), "Keep-Alive", List.of("timeout=5")),
                (k, v) -> true
        );
        when(mockHttpResponse.headers()).thenReturn(responseHeaders);

        // Mocking the generic method send correctly
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());

        // Mock Servlet Response Output Stream
        when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        // Execute
        requestProxy.proxyRequest(mockRequest, mockResponse, targetUri, properties);

        // Verify HttpClient got the right built request
        verify(mockHttpClient).send(httpRequestCaptor.capture(), any());
        HttpRequest sentRequest = httpRequestCaptor.getValue();

        assertEquals("POST", sentRequest.method());
        assertEquals(new URI("http://backend-service:8080/api/test?param=value"), sentRequest.uri());
        assertTrue(sentRequest.headers().map().containsKey("Content-Type"));
        // Hop-by-hop should not be copied
        assertTrue(sentRequest.headers().map().keySet().stream().noneMatch(k -> k.equalsIgnoreCase("Connection")));

        // Verify Servlet Response was populated correctly
        verify(mockResponse).setStatus(201);
        verify(mockResponse).addHeader("Custom-Header", "custom-value");
        // Hop-by-hop response header should not be copied
        verify(mockResponse, never()).addHeader(eq("Keep-Alive"), anyString());
        
        // With transferTo, it writes in chunks. Verify at least one write occurred.
        verify(mockOutputStream, atLeastOnce()).write(any(byte[].class), anyInt(), anyInt());
    }

    @Test
    void testProxyRequest_WithResponseTimeout() throws Exception {
        properties.setResponseTimeout(Duration.ofSeconds(10));
        
        when(mockRequest.getMethod()).thenReturn("GET");
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        when(mockRequest.getContextPath()).thenReturn("");
        when(mockRequest.getQueryString()).thenReturn(null);
        when(mockRequest.getHeaderNames()).thenReturn(Collections.emptyEnumeration());

        when(mockHttpResponse.statusCode()).thenReturn(200);
        InputStream responseStream = new ByteArrayInputStream(new byte[0]);
        when(mockHttpResponse.body()).thenReturn(responseStream);
        when(mockHttpResponse.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));
        
        // Mocking the generic method send correctly
        doReturn(mockHttpResponse).when(mockHttpClient).send(any(HttpRequest.class), ArgumentMatchers.<HttpResponse.BodyHandler<InputStream>>any());

        // Use lenient here because the response body is empty (length 0), so it might not write to the output stream
        lenient().when(mockResponse.getOutputStream()).thenReturn(mockOutputStream);

        requestProxy.proxyRequest(mockRequest, mockResponse, targetUri, properties);

        verify(mockHttpClient).send(httpRequestCaptor.capture(), any());
        HttpRequest sentRequest = httpRequestCaptor.getValue();

        assertTrue(sentRequest.timeout().isPresent());
        assertEquals(Duration.ofSeconds(10), sentRequest.timeout().get());
    }
}
