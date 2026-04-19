package com.nanogate.security.filter;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.unit.DataSize;

import java.io.IOException;

@Component
@Order(-100) // Run this filter very early to reject requests quickly
public class RequestSizeLimitFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RequestSizeLimitFilter.class);

    private final long maxRequestBodySize;

    public RequestSizeLimitFilter(@Value("${nanogate.security.max-request-body-size:10MB}") String maxRequestBodySize) {
        this.maxRequestBodySize = DataSize.parse(maxRequestBodySize).toBytes();
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        long contentLength = request.getContentLengthLong();

        if (contentLength > maxRequestBodySize) {
            log.warn("Request rejected: body size ({}) exceeds the configured limit of {} bytes.", contentLength, maxRequestBodySize);
            ((HttpServletResponse) servletResponse).sendError(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE, "Request body exceeds limit");
            return;
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
