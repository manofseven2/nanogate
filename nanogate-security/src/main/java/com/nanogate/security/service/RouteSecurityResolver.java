package com.nanogate.security.service;

import com.nanogate.security.model.SecurityProperties;
import jakarta.servlet.http.HttpServletRequest;

import java.util.List;
import java.util.Map;

/**
 * Interface to resolve security policies for a request.
 * Implemented by nanogate-routing to bridge with the RouteRegistry.
 */
public interface RouteSecurityResolver {

    ResolvedSecurityPolicy resolvePolicy(HttpServletRequest request);

    record ResolvedSecurityPolicy(
            boolean enabled,
            List<String> requiredScopes,
            List<String> requiredRoles,
            Map<String, String> forwardClaims,
            String matchedRouteId // For logging
    ) {}
}
