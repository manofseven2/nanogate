package com.nanogate.routing.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.io.IOException;

import static org.mockito.Mockito.*;

class AccessLoggingFilterTest {

    private AccessLoggingFilter accessLoggingFilter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        // Assume default X-Forwarded-For header
        accessLoggingFilter = new AccessLoggingFilter("X-Forwarded-For");
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void testDoFilter_ValidRequest_ChainContinues() throws ServletException, IOException {
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.setRemoteAddr("192.168.1.1");
        response.setStatus(200);

        accessLoggingFilter.doFilter(request, response, filterChain);

        // Verify the chain was continued
        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilter_WithXForwardedForHeader() throws ServletException, IOException {
        request.setMethod("POST");
        request.setRequestURI("/api/submit");
        request.addHeader("X-Forwarded-For", "10.0.0.1, 192.168.1.1");
        request.setRemoteAddr("127.0.0.1"); // Proxy IP
        response.setStatus(201);

        accessLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }

    @Test
    void testDoFilter_WithDifferentConfiguredHeader() throws ServletException, IOException {
        accessLoggingFilter = new AccessLoggingFilter("X-Real-IP");
        request.setMethod("GET");
        request.setRequestURI("/api/submit");
        request.addHeader("X-Real-IP", "10.0.0.5");
        request.setRemoteAddr("127.0.0.1"); // Proxy IP
        response.setStatus(200);

        accessLoggingFilter.doFilter(request, response, filterChain);

        verify(filterChain, times(1)).doFilter(request, response);
    }
}
