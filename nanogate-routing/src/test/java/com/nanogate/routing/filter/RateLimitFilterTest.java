package com.nanogate.routing.filter;

import com.nanogate.resilience.service.RateLimiterService;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.RateLimitProperties;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.ratelimit.RateLimitKeyResolver;
import com.nanogate.routing.ratelimit.RateLimitKeyResolverFactory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class RateLimitFilterTest {

    private RateLimiterService rateLimiterService;
    private RateLimitKeyResolverFactory keyResolverFactory;
    private NanoGateRouteProperties routeProperties;
    private RateLimitFilter filter;

    private HttpServletRequest request;
    private HttpServletResponse response;
    private FilterChain filterChain;
    private RateLimitKeyResolver keyResolver;

    @BeforeEach
    void setUp() {
        rateLimiterService = mock(RateLimiterService.class);
        keyResolverFactory = mock(RateLimitKeyResolverFactory.class);
        routeProperties = mock(NanoGateRouteProperties.class);
        filter = new RateLimitFilter(rateLimiterService, keyResolverFactory, routeProperties);

        request = mock(HttpServletRequest.class);
        response = mock(HttpServletResponse.class);
        filterChain = mock(FilterChain.class);
        keyResolver = mock(RateLimitKeyResolver.class);
    }

    @Test
    void doFilter_skipsIfNoRoute() throws Exception {
        when(request.getAttribute("NANO_ROUTE")).thenReturn(null);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void doFilter_skipsIfNoRateLimitConfigured() throws Exception {
        Route route = new Route();
        route.setId("route1");
        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);
        // Properties return null by default

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(rateLimiterService);
    }

    @Test
    void doFilter_allowsRequestIfPermissionAcquired() throws Exception {
        Route route = new Route();
        route.setId("route1");
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerSecond(10);
        props.setResolver("IP");
        route.setRateLimit(props);

        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);
        when(keyResolverFactory.getResolver("IP")).thenReturn(keyResolver);
        when(keyResolver.resolve(request, props)).thenReturn("127.0.0.1");
        when(rateLimiterService.acquirePermission("ROUTE_route1:127.0.0.1", 10)).thenReturn(true);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_blocksRequestIfPermissionDenied() throws Exception {
        Route route = new Route();
        route.setId("route1");
        RateLimitProperties props = new RateLimitProperties();
        props.setRequestsPerSecond(10);
        props.setResolver("IP");
        route.setRateLimit(props);

        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);
        when(keyResolverFactory.getResolver("IP")).thenReturn(keyResolver);
        when(keyResolver.resolve(request, props)).thenReturn("127.0.0.1");
        when(rateLimiterService.acquirePermission("ROUTE_route1:127.0.0.1", 10)).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        verify(response).sendError(429, "Too Many Requests");
        verifyNoInteractions(filterChain);
    }
}
