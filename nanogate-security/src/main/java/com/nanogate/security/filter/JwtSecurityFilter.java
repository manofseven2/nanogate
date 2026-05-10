package com.nanogate.security.filter;

import com.nanogate.security.service.RouteSecurityResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Filter that enforces the resolved security policy for a route.
 */
@Component
@Order(-100) // Run very early, before RateLimit or Proxy
public class JwtSecurityFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtSecurityFilter.class);

    private final RouteSecurityResolver policyResolver;
    private final JwtDecoder jwtDecoder;

    public JwtSecurityFilter(RouteSecurityResolver policyResolver,
                             Optional<JwtDecoder> jwtDecoder) {
        this.policyResolver = policyResolver;
        this.jwtDecoder = jwtDecoder.orElse(null);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        RouteSecurityResolver.ResolvedSecurityPolicy policy = policyResolver.resolvePolicy(request);

        if (!policy.enabled()) {
            filterChain.doFilter(request, response);
            return;
        }

        // If enabled and no route found, we block (as requested)
        if (policy.matchedRouteId() == null) {
            log.warn("Security is enabled but no route matched for URI {}. Blocking request.", request.getRequestURI());
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "No matching route found for secured gateway");
            return;
        }

        String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            log.warn("Missing or invalid Authorization header for secured route {}", policy.matchedRouteId());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Bearer token required");
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (jwtDecoder == null) {
                throw new IllegalStateException("JWT Decoder is not configured. Check gateway security properties.");
            }

            Jwt jwt = jwtDecoder.decode(token);
            
            // Validate Scopes & Roles
            if (!authorize(jwt, policy)) {
                log.warn("Insufficient permissions for route {}. Required Scopes: {}, Roles: {}", 
                    policy.matchedRouteId(), policy.requiredScopes(), policy.requiredRoles());
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Insufficient permissions");
                return;
            }

            // Store in SecurityContext for downstream filters if needed
            Authentication auth = new JwtAuthenticationToken(jwt);
            SecurityContextHolder.getContext().setAuthentication(auth);
            
            // Store resolved policy in request attributes for ClaimForwardingFilter
            request.setAttribute("nanogate.resolved_security_policy", policy);

            filterChain.doFilter(request, response);

        } catch (Exception e) {
            log.warn("JWT validation failed: {}", e.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: " + e.getMessage());
        }
    }

    private boolean authorize(Jwt jwt, RouteSecurityResolver.ResolvedSecurityPolicy policy) {
        // 1. Check Scopes
        List<String> requiredScopes = policy.requiredScopes();
        if (!requiredScopes.isEmpty()) {
            List<String> tokenScopes = jwt.getClaimAsStringList("scp");
            if (tokenScopes == null) tokenScopes = jwt.getClaimAsStringList("scope");
            if (tokenScopes == null) return false;
            if (Collections.disjoint(tokenScopes, requiredScopes)) return false;
        }

        // 2. Check Roles
        List<String> requiredRoles = policy.requiredRoles();
        if (!requiredRoles.isEmpty()) {
            // Keycloak style realm_access.roles or custom claim
            List<String> tokenRoles = jwt.getClaimAsStringList("roles");
            if (tokenRoles == null) {
                // Try to navigate map structure for Keycloak: realm_access -> roles
                Object realmAccess = jwt.getClaim("realm_access");
                if (realmAccess instanceof java.util.Map) {
                    Object roles = ((java.util.Map<?, ?>) realmAccess).get("roles");
                    if (roles instanceof Collection) {
                        tokenRoles = ((Collection<?>) roles).stream().map(Object::toString).toList();
                    }
                }
            }
            if (tokenRoles == null) return false;
            if (Collections.disjoint(tokenRoles, requiredRoles)) return false;
        }

        return true;
    }
}
