package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves to a constant global key.
 * Useful when you want to rate limit a route or backend globally,
 * regardless of the client's IP or headers.
 */
@Component
public class GlobalRateLimitKeyResolver implements RateLimitKeyResolver {

    @Override
    public String name() {
        return "GLOBAL";
    }

    @Override
    public String resolve(HttpServletRequest request, RateLimitProperties properties) {
        return "ALL";
    }
}
