package com.nanogate.caching.filter;

import com.nanogate.caching.model.CachedResponse;
import com.nanogate.caching.service.ResponseCacheService;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.CacheProperties;
import com.nanogate.routing.model.Route;
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
import org.springframework.util.CollectionUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
@Order(40)
public class ResponseCacheFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(ResponseCacheFilter.class);

    private final ResponseCacheService cacheService;

    public ResponseCacheFilter(ResponseCacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain) throws IOException, ServletException {
        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (!"GET".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        Route route = (Route) request.getAttribute("NANO_ROUTE");
        CacheProperties cacheProps = resolveCacheProperties(route);

        if (cacheProps == null || Boolean.FALSE.equals(cacheProps.getEnabled())) {
            filterChain.doFilter(request, response);
            return;
        }

        String cacheKey = generateCacheKey(request, cacheProps);
        Optional<CachedResponse> optionalCached = cacheService.get(cacheKey);

        if (optionalCached.isPresent()) {
            log.debug("Cache hit for key: {}", cacheKey);
            CachedResponse cached = optionalCached.get();
            response.setStatus(cached.getStatusCode());
            cached.getHeaders().forEach((name, values) -> {
                values.forEach(val -> response.addHeader(name, val));
            });
            response.addHeader("X-NanoGate-Cache", "HIT");
            response.getOutputStream().write(cached.getBody());
            response.getOutputStream().flush();
            return;
        }

        log.debug("Cache miss for key: {}", cacheKey);
        
        GatewayCachingResponseWrapper responseWrapper = new GatewayCachingResponseWrapper(response);
        filterChain.doFilter(request, responseWrapper);

        if (responseWrapper.getStatus() == HttpServletResponse.SC_OK) {
            byte[] body = responseWrapper.getCachedBody();
            if (body != null && body.length > 0) {
                Map<String, List<String>> headers = extractHeaders(responseWrapper);
                CachedResponse cachedResponse = new CachedResponse(responseWrapper.getStatus(), headers, body);
                cacheService.put(cacheKey, cachedResponse, cacheProps.getTtl());
            } else {
                log.debug("Response for key {} was not cacheable (e.g. stream, chunked, or too large).", cacheKey);
            }
        }
    }

    private String generateCacheKey(HttpServletRequest request, CacheProperties cacheProps) {
        StringBuilder keyBuilder = new StringBuilder();
        keyBuilder.append(request.getMethod()).append(":");
        keyBuilder.append(request.getRequestURI());
        
        if (request.getQueryString() != null) {
            keyBuilder.append("?").append(request.getQueryString());
        }

        if (cacheProps.getVaryByHeaders() != null && !cacheProps.getVaryByHeaders().isEmpty()) {
            for (String headerName : cacheProps.getVaryByHeaders()) {
                String headerVal = request.getHeader(headerName);
                if (headerVal != null) {
                    keyBuilder.append("|").append(headerName).append("=").append(headerVal);
                }
            }
        }
        return keyBuilder.toString();
    }

    private Map<String, List<String>> extractHeaders(GatewayCachingResponseWrapper wrapper) {
        Map<String, List<String>> headers = new HashMap<>();
        for (String headerName : wrapper.getHeaderNames()) {
            if (headerName.equalsIgnoreCase("X-NanoGate-Cache") || headerName.equalsIgnoreCase("Transfer-Encoding") || headerName.equalsIgnoreCase("Connection")) {
                continue;
            }
            Collection<String> values = wrapper.getHeaders(headerName);
            headers.put(headerName, new ArrayList<>(values));
        }
        return headers;
    }

    private CacheProperties resolveCacheProperties(Route route) {
        if (route == null) return null;
        return route.getCache();
    }

    /**
     * Custom wrapper that writes to the client IMMEDIATELY while simultaneously buffering in memory.
     * If the response is a stream, chunked, or exceeds 5MB, it stops buffering to prevent OOM,
     * but continues to write to the client seamlessly.
     */
    private static class GatewayCachingResponseWrapper extends jakarta.servlet.http.HttpServletResponseWrapper {
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        private final jakarta.servlet.ServletOutputStream originalStream;
        private jakarta.servlet.ServletOutputStream proxyStream;
        private boolean cacheable = true;
        private static final int MAX_CACHE_SIZE = 5 * 1024 * 1024; // 5MB

        public GatewayCachingResponseWrapper(HttpServletResponse response) throws IOException {
            super(response);
            this.originalStream = response.getOutputStream();
            super.addHeader("X-NanoGate-Cache", "MISS");
        }

        @Override
        public void setHeader(String name, String value) {
            super.setHeader(name, value);
            checkCacheable(name, value);
        }

        @Override
        public void addHeader(String name, String value) {
            super.addHeader(name, value);
            checkCacheable(name, value);
        }

        private void checkCacheable(String name, String value) {
            if (!cacheable) return;
            if ("Content-Type".equalsIgnoreCase(name) && value != null && 
               (value.contains("text/event-stream") || value.contains("application/stream+json"))) {
                cacheable = false;
            }
            if ("Transfer-Encoding".equalsIgnoreCase(name) && "chunked".equalsIgnoreCase(value)) {
                cacheable = false;
            }
        }

        @Override
        public jakarta.servlet.ServletOutputStream getOutputStream() throws IOException {
            if (proxyStream == null) {
                proxyStream = new jakarta.servlet.ServletOutputStream() {
                    @Override
                    public boolean isReady() { return originalStream.isReady(); }
                    @Override
                    public void setWriteListener(jakarta.servlet.WriteListener writeListener) { originalStream.setWriteListener(writeListener); }

                    @Override
                    public void write(int b) throws IOException {
                        originalStream.write(b);
                        if (cacheable) {
                            if (buffer.size() < MAX_CACHE_SIZE) {
                                buffer.write(b);
                            } else {
                                cacheable = false;
                            }
                        }
                    }

                    @Override
                    public void write(byte[] b, int off, int len) throws IOException {
                        originalStream.write(b, off, len);
                        if (cacheable) {
                            if (buffer.size() + len <= MAX_CACHE_SIZE) {
                                buffer.write(b, off, len);
                            } else {
                                cacheable = false;
                            }
                        }
                    }

                    @Override
                    public void flush() throws IOException { originalStream.flush(); }
                    @Override
                    public void close() throws IOException { originalStream.close(); }
                };
            }
            return proxyStream;
        }

        public byte[] getCachedBody() {
            return cacheable ? buffer.toByteArray() : null;
        }
    }
}
