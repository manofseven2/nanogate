package com.nanogate.security.filter;

import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.*;

/**
 * Filter that extracts claims from the validated JWT and adds them as HTTP headers
 * for the downstream backend services.
 */
@Component
@Order(-90) // Run right after JwtSecurityFilter
public class ClaimForwardingFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RouteSecurityResolver.ResolvedSecurityPolicy policy = 
            (RouteSecurityResolver.ResolvedSecurityPolicy) request.getAttribute("nanogate.resolved_security_policy");

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();

        if (policy == null || policy.forwardClaims().isEmpty() || auth == null || !(auth.getPrincipal() instanceof Jwt jwt)) {
            filterChain.doFilter(request, response);
            return;
        }

        // We use a wrapper to "add" headers for the rest of the NanoGate filter chain
        Map<String, String> extraHeaders = new HashMap<>();
        
        policy.forwardClaims().forEach((claimName, headerName) -> {
            Object claimValue = jwt.getClaim(claimName);
            if (claimValue != null) {
                extraHeaders.put(headerName, claimValue.toString());
            }
        });

        if (extraHeaders.isEmpty()) {
            filterChain.doFilter(request, response);
        } else {
            filterChain.doFilter(new HeaderAugmentingRequestWrapper(request, extraHeaders), response);
        }
    }

    private static class HeaderAugmentingRequestWrapper extends HttpServletRequestWrapper {
        private final Map<String, String> extraHeaders;

        public HeaderAugmentingRequestWrapper(HttpServletRequest request, Map<String, String> extraHeaders) {
            super(request);
            this.extraHeaders = extraHeaders;
        }

        @Override
        public String getHeader(String name) {
            String value = extraHeaders.get(name);
            if (value != null) return value;
            return super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            List<String> names = Collections.list(super.getHeaderNames());
            names.addAll(extraHeaders.keySet());
            return Collections.enumeration(names);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            String value = extraHeaders.get(name);
            if (value != null) {
                return Collections.enumeration(Collections.singletonList(value));
            }
            return super.getHeaders(name);
        }
    }
}
