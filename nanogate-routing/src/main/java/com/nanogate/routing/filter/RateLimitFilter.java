package com.nanogate.routing.filter;

import com.nanogate.resilience.service.RateLimiterService;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.RateLimitProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.ratelimit.RateLimitKeyResolver;
import com.nanogate.routing.ratelimit.RateLimitKeyResolverFactory;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(20) // Runs after RouteResolutionFilter (1) and IpSetFilter (10)
public class RateLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    private final RateLimiterService rateLimiterService;
    private final RateLimitKeyResolverFactory keyResolverFactory;
    private final NanoGateRouteProperties routeProperties;

    public RateLimitFilter(RateLimiterService rateLimiterService,
                           RateLimitKeyResolverFactory keyResolverFactory,
                           NanoGateRouteProperties routeProperties) {
        this.rateLimiterService = rateLimiterService;
        this.keyResolverFactory = keyResolverFactory;
        this.routeProperties = routeProperties;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        Route route = (Route) request.getAttribute("NANO_ROUTE");
        if (route == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        RateLimitConfig rateLimitConfig = resolveRateLimitConfig(route);

        if (rateLimitConfig == null || rateLimitConfig.properties.getRequestsPerSecond() == null) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        RateLimitProperties rateLimit = rateLimitConfig.properties;
        RateLimitKeyResolver keyResolver = keyResolverFactory.getResolver(rateLimit.getResolver());
        String key = keyResolver.resolve(request, rateLimit);
        
        // Scope the key to where the configuration originated (Route, BackendSet, or Global)
        String namespacedKey = rateLimitConfig.namespace + ":" + key;

        boolean allowed = rateLimiterService.acquirePermission(namespacedKey, rateLimit.getRequestsPerSecond());

        if (!allowed) {
            log.warn("Rate limit exceeded for route '{}' with key '{}'", route.getId(), namespacedKey);
            response.sendError(429, "Too Many Requests");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }

    private RateLimitConfig resolveRateLimitConfig(Route route) {
        if (route.getRateLimit() != null) {
            return new RateLimitConfig("ROUTE_" + route.getId(), route.getRateLimit());
        }

        if (route.getBackendSet() != null) {
            BackendSet backendSet = routeProperties.getBackendSet(route.getBackendSet());
            if (backendSet != null && backendSet.getRateLimit() != null) {
                return new RateLimitConfig("BACKEND_" + backendSet.getName(), backendSet.getRateLimit());
            }
        }

        if (routeProperties.getDefaultRateLimit() != null) {
            return new RateLimitConfig("GLOBAL", routeProperties.getDefaultRateLimit());
        }

        return null;
    }

    private static class RateLimitConfig {
        final String namespace;
        final RateLimitProperties properties;

        RateLimitConfig(String namespace, RateLimitProperties properties) {
            this.namespace = namespace;
            this.properties = properties;
        }
    }
}
