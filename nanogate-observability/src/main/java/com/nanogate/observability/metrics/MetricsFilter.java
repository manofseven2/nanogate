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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE) // Run this filter before all others to capture full overhead
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
        // Retrieve matched route from attribute to avoid redundant lookup
        Route route = (Route) request.getAttribute("nanogate.matched_route");
        if (route == null) {
            // Fallback for cases where RouteResolutionFilter hasn't run yet or failed
            route = routeLocator.findRoute(request).orElse(null);
        }

        String pathTag = route != null ? route.getPath() : request.getRequestURI();
        String status = String.valueOf(response.getStatus());

        Long startTime = (Long) request.getAttribute(MetricAttribute.START_TIME_NANOS.name());
        if (startTime != null) {
            long totalDuration = System.nanoTime() - startTime;
            meterRegistry.timer("nanogate.requests.total", "path", pathTag, "status", status)
                         .record(totalDuration, TimeUnit.NANOSECONDS);

            // Calculate overhead centrally: Total - Backend (if any)
            Long backendDuration = (Long) request.getAttribute(MetricAttribute.BACKEND_DURATION_NANOS.name());
            long overheadDuration = Math.max(0, (backendDuration != null) ? (totalDuration - backendDuration) : totalDuration);
            
            meterRegistry.timer("nanogate.request.overhead", "path", pathTag)
                         .record(overheadDuration, TimeUnit.NANOSECONDS);

            if (backendDuration != null) {
                meterRegistry.timer("nanogate.backend.response", "path", pathTag, "status", status)
                             .record(backendDuration, TimeUnit.NANOSECONDS);
            }
        }
    }
}
