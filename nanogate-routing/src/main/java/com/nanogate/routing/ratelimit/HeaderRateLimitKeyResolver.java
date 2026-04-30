package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

/**
 * Resolves the rate limit bucket key from a specific HTTP header.
 */
@Component
public class HeaderRateLimitKeyResolver implements RateLimitKeyResolver {

    @Override
    public String name() {
        return "HEADER";
    }

    @Override
    public String resolve(HttpServletRequest request, RateLimitProperties properties) {
        if (properties.getResolverArg() == null || properties.getResolverArg().isBlank()) {
            throw new IllegalArgumentException("HEADER RateLimitKeyResolver requires a 'resolverArg' specifying the header name.");
        }

        String headerValue = request.getHeader(properties.getResolverArg());
        if (headerValue == null || headerValue.isEmpty()) {
            // Fallback strategy when header is missing? For now, we rate limit them under "anonymous" or IP
            return "MISSING_HEADER:" + request.getRemoteAddr();
        }

        return headerValue;
    }
}
