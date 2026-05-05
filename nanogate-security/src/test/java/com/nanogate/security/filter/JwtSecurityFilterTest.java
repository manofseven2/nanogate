package com.nanogate.security.filter;

import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.Mockito.*;

class JwtSecurityFilterTest {

    private JwtSecurityFilter filter;
    private RouteSecurityResolver resolver;
    private JwtDecoder decoder;
    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain chain;

    @BeforeEach
    void setUp() {
        resolver = mock(RouteSecurityResolver.class);
        decoder = mock(JwtDecoder.class);
        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        chain = mock(FilterChain.class);
        filter = new JwtSecurityFilter(resolver, Optional.of(decoder));
    }

    @Test
    void shouldPassWhenSecurityDisabled() throws Exception {
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                false, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), "test-route"
        );
        when(resolver.resolvePolicy(request)).thenReturn(policy);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        verifyNoInteractions(decoder);
    }

    @Test
    void shouldBlockWhenNoRouteFoundOnSecuredGateway() throws Exception {
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), null
        );
        when(resolver.resolvePolicy(request)).thenReturn(policy);

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }

    @Test
    void shouldBlockWhenMissingAuthorizationHeader() throws Exception {
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), "test-route"
        );
        when(resolver.resolvePolicy(request)).thenReturn(policy);
        when(request.getHeader("Authorization")).thenReturn(null);

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_UNAUTHORIZED), anyString());
    }

    @Test
    void shouldSuccessWithValidToken() throws Exception {
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                true, Collections.emptyList(), Collections.emptyList(), Collections.emptyMap(), "test-route"
        );
        when(resolver.resolvePolicy(request)).thenReturn(policy);
        when(request.getHeader("Authorization")).thenReturn("Bearer valid-token");
        
        Jwt jwt = mock(Jwt.class);
        when(decoder.decode("valid-token")).thenReturn(jwt);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(any(), eq(response));
        verify(request).setAttribute(eq("nanogate.resolved_security_policy"), eq(policy));
    }

    @Test
    void shouldBlockWhenInsufficientScopes() throws Exception {
        RouteSecurityResolver.ResolvedSecurityPolicy policy = new RouteSecurityResolver.ResolvedSecurityPolicy(
                true, List.of("required.scope"), Collections.emptyList(), Collections.emptyMap(), "test-route"
        );
        when(resolver.resolvePolicy(request)).thenReturn(policy);
        when(request.getHeader("Authorization")).thenReturn("Bearer token");

        Jwt jwt = mock(Jwt.class);
        when(jwt.getClaimAsStringList("scp")).thenReturn(List.of("wrong.scope"));
        when(decoder.decode("token")).thenReturn(jwt);

        filter.doFilter(request, response, chain);

        verify(response).sendError(eq(HttpServletResponse.SC_FORBIDDEN), anyString());
    }
}
