package com.nanogate.observability.metrics;

import com.nanogate.routing.metrics.MetricAttribute;
import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RouteLocator;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Order(0) // Run this filter before all others
public class MetricsFilter implements Filter {

    private final MeterRegistry meterRegistry;
    private final RouteLocator routeLocator;

    public MetricsFilter(MeterRegistry meterRegistry, RouteLocator routeLocator) {
        this.meterRegistry = meterRegistry;
        this.routeLocator = routeLocator;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        servletRequest.setAttribute(MetricAttribute.START_TIME_NANOS.name(), System.nanoTime());

        try {
            filterChain.doFilter(servletRequest, servletResponse);
        } finally {
            recordMetrics((HttpServletRequest) servletRequest, (HttpServletResponse) servletResponse);
        }
    }

    private void recordMetrics(HttpServletRequest request, HttpServletResponse response) {
        // Use the route's path pattern if a route is found, otherwise, use the request URI.
        // This allows for monitoring unrouted traffic (like actuator endpoints or 404s)
        // while being aware of the high cardinality risk if exposed to many unique, invalid URLs.
        String pathTag = routeLocator.findRoute(request)
                .map(Route::getPath)
                .orElse(request.getRequestURI());

        String status = String.valueOf(response.getStatus());

        Long startTime = (Long) request.getAttribute(MetricAttribute.START_TIME_NANOS.name());
        if (startTime != null) {
            long totalDuration = System.nanoTime() - startTime;
            meterRegistry.timer("nanogate.requests.total", "path", pathTag, "status", status)
                         .record(totalDuration, TimeUnit.NANOSECONDS);
        }

        Long overheadDuration = (Long) request.getAttribute(MetricAttribute.OVERHEAD_DURATION_NANOS.name());
        if (overheadDuration != null) {
            meterRegistry.timer("nanogate.request.overhead", "path", pathTag)
                         .record(overheadDuration, TimeUnit.NANOSECONDS);
        }

        Long backendDuration = (Long) request.getAttribute(MetricAttribute.BACKEND_DURATION_NANOS.name());
        if (backendDuration != null) {
            meterRegistry.timer("nanogate.backend.response", "path", pathTag, "status", status)
                         .record(backendDuration, TimeUnit.NANOSECONDS);
        }
    }
}
