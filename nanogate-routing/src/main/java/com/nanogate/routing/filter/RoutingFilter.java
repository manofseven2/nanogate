package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RequestOrchestrator;
import com.nanogate.routing.service.RouteLocator;
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

import java.io.IOException;
import java.util.Optional;

@Component
@Order(1) // Runs after the MetricsFilter
public class RoutingFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RoutingFilter.class);

    private final RouteLocator routeLocator;
    private final RequestOrchestrator requestOrchestrator;

    @Value("${management.endpoints.web.base-path:/actuator}")
    private String actuatorBasePath;

    public RoutingFilter(RouteLocator routeLocator, RequestOrchestrator requestOrchestrator) {
        this.routeLocator = routeLocator;
        this.requestOrchestrator = requestOrchestrator;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        if (request.getRequestURI().startsWith(actuatorBasePath)) {
            filterChain.doFilter(servletRequest, servletResponse);
            return;
        }

        Optional<Route> optionalRoute = routeLocator.findRoute(request);

        if (optionalRoute.isEmpty()) {
            log.warn("No route matched for request: {} {}", request.getMethod(), request.getRequestURI());
            response.sendError(HttpServletResponse.SC_NOT_FOUND, "Not Found");
            return;
        }

        requestOrchestrator.orchestrate(request, response, optionalRoute.get());
    }
}
