package com.nanogate.security.filter;

import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ClaimForwardingFilterTest {

    @Test
    void shouldForwardClaimsAsHeaders() throws Exception {
        ClaimForwardingFilter filter = new ClaimForwardingFilter();
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        // Mock Policy
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                true, Collections.emptyList(), Collections.emptyList(), 
                Map.of("sub", "X-User-Id", "email", "X-User-Email"), "test-route"
        );
        when(request.getAttribute("nanogate.resolved_security_policy")).thenReturn(policy);

        // Mock Security Context
        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaim("sub")).thenReturn("user-123");
        when(jwt.getClaim("email")).thenReturn("user@example.com");
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));

        filter.doFilter(request, response, chain);

        // Verify that a wrapped request was passed to the chain
        ArgumentCaptor<HttpServletRequest> requestCaptor = ArgumentCaptor.forClass(HttpServletRequest.class);
        verify(chain).doFilter(requestCaptor.capture(), eq(response));

        HttpServletRequest wrappedRequest = requestCaptor.getValue();
        assertEquals("user-123", wrappedRequest.getHeader("X-User-Id"));
        assertEquals("user@example.com", wrappedRequest.getHeader("X-User-Email"));
        
        SecurityContextHolder.clearContext();
    }
}
