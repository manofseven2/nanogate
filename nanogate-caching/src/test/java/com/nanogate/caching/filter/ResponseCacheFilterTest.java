package com.nanogate.caching.filter;

import com.nanogate.caching.model.CachedResponse;
import com.nanogate.caching.service.ResponseCacheService;
import com.nanogate.routing.model.CacheProperties;
import com.nanogate.routing.model.Route;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ResponseCacheFilterTest {

    private ResponseCacheService cacheService;
    private ResponseCacheFilter filter;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        cacheService = mock(ResponseCacheService.class);
        filter = new ResponseCacheFilter(cacheService);
        request = new MockHttpServletRequest();
        response = new MockHttpServletResponse();
        filterChain = mock(FilterChain.class);
    }

    @Test
    void doFilter_skipsNonGetRequests() throws Exception {
        request.setMethod("POST");

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(cacheService);
    }

    @Test
    void doFilter_skipsWhenCachingDisabled() throws Exception {
        request.setMethod("GET");
        Route route = new Route();
        CacheProperties cacheProps = new CacheProperties();
        cacheProps.setEnabled(false);
        route.setCache(cacheProps);
        request.setAttribute("NANO_ROUTE", route);

        filter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(cacheService);
    }

    @Test
    void doFilter_returnsCachedResponseOnHit() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/test");

        Route route = new Route();
        CacheProperties cacheProps = new CacheProperties();
        cacheProps.setEnabled(true);
        route.setCache(cacheProps);
        request.setAttribute("NANO_ROUTE", route);

        Map<String, List<String>> headers = new HashMap<>();
        headers.put("Content-Type", List.of("application/json"));
        CachedResponse cachedResponse = new CachedResponse(200, headers, "cached-body".getBytes());

        when(cacheService.get("GET:/api/test")).thenReturn(Optional.of(cachedResponse));

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("application/json", response.getHeader("Content-Type"));
        assertEquals("HIT", response.getHeader("X-NanoGate-Cache"));
        assertEquals("cached-body", response.getContentAsString());

        verifyNoInteractions(filterChain);
    }

    @Test
    void doFilter_cachesResponseOnMiss() throws Exception {
        request.setMethod("GET");
        request.setRequestURI("/api/test");
        request.setQueryString("id=1");

        Route route = new Route();
        CacheProperties cacheProps = new CacheProperties();
        cacheProps.setEnabled(true);
        cacheProps.setTtl(Duration.ofSeconds(60));
        route.setCache(cacheProps);
        request.setAttribute("NANO_ROUTE", route);

        when(cacheService.get("GET:/api/test?id=1")).thenReturn(Optional.empty());

        doAnswer(invocation -> {
            jakarta.servlet.http.HttpServletResponseWrapper wrapper = invocation.getArgument(1);
            wrapper.setStatus(200);
            wrapper.addHeader("Content-Type", "text/plain");
            wrapper.getOutputStream().write("fresh-body".getBytes());
            return null;
        }).when(filterChain).doFilter(eq(request), any(jakarta.servlet.http.HttpServletResponseWrapper.class));

        filter.doFilter(request, response, filterChain);

        assertEquals(200, response.getStatus());
        assertEquals("text/plain", response.getHeader("Content-Type"));
        assertEquals("MISS", response.getHeader("X-NanoGate-Cache"));
        assertEquals("fresh-body", response.getContentAsString());

        verify(cacheService).put(eq("GET:/api/test?id=1"), any(CachedResponse.class), eq(Duration.ofSeconds(60)));
    }
}
