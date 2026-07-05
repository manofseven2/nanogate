package com.nanogate.routing.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

import static net.logstash.logback.argument.StructuredArguments.kv;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AccessLoggingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(AccessLoggingFilter.class);

    private final String ipHeader;

    public AccessLoggingFilter(
            @org.springframework.beans.factory.annotation.Value("${nanogate.security.ip-header:X-Forwarded-For}") String ipHeader) {
        this.ipHeader = ipHeader;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        if (!(servletRequest instanceof HttpServletRequest request) || !(servletResponse instanceof HttpServletResponse response)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        long startTime = System.nanoTime();

        try {
            filterChain.doFilter(request, response);
        } catch (Exception e) {
            log.error("Unhandled server exception: {}", e.getMessage(), e,
                    kv("error_type", "UnhandledServerException"));
            response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Internal Server Error");
        } finally {
            long durationNs = System.nanoTime() - startTime;
            long durationMs = durationNs / 1_000_000;

            String uri = request.getRequestURI();
            String method = request.getMethod();
            int statusCode = response.getStatus();
            String clientIp = getClientIpAddress(request);

            log.info("Access log: {} {} - {}", method, uri, statusCode,
                    kv("method", method),
                    kv("uri", uri),
                    kv("status_code", statusCode),
                    kv("duration_ms", durationMs),
                    kv("client_ip", clientIp));
        }
    }

    private String getClientIpAddress(HttpServletRequest request) {
        String headerValue = request.getHeader(ipHeader);
        if (headerValue != null && !headerValue.isEmpty()) {
            return headerValue.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }
}
