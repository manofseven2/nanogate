package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Resolves the client IP address to use as the rate limit bucket key.
 */
@Component
public class IpRateLimitKeyResolver implements RateLimitKeyResolver {

    private final String ipHeader;

    public IpRateLimitKeyResolver(
            @Value("${nanogate.security.ip-header:X-Forwarded-For}") String ipHeader) {
        this.ipHeader = ipHeader;
    }

    @Override
    public String name() {
        return "IP";
    }

    @Override
    public String resolve(HttpServletRequest request, RateLimitProperties properties) {
        String clientIp = request.getHeader(ipHeader);
        if (clientIp == null || clientIp.isEmpty()) {
            clientIp = request.getRemoteAddr();
        } else {
            clientIp = clientIp.split(",")[0].trim();
        }
        return clientIp;
    }
}
