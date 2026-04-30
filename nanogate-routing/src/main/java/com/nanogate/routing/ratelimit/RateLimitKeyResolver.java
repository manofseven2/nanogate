package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;

/**
 * Strategy interface for resolving a unique rate limiting key from an HTTP request.
 */
public interface RateLimitKeyResolver {

    /**
     * @return The identifier for this resolver (e.g., "IP", "HEADER").
     */
    String name();

    /**
     * Resolves the key from the incoming request.
     *
     * @param request    The incoming HTTP request.
     * @param properties The rate limit configuration that might contain arguments for the resolver.
     * @return The resolved key.
     */
    String resolve(HttpServletRequest request, RateLimitProperties properties);
}
