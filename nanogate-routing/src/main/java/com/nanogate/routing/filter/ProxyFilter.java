package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RequestOrchestrator;
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

@Component
@Order(100) // Runs last in the NanoGate chain, after security, rate limiting, etc.
public class ProxyFilter implements Filter {

    private final RequestOrchestrator requestOrchestrator;

    public ProxyFilter(RequestOrchestrator requestOrchestrator) {
        this.requestOrchestrator = requestOrchestrator;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        HttpServletRequest request = (HttpServletRequest) servletRequest;
        HttpServletResponse response = (HttpServletResponse) servletResponse;

        Route route = (Route) request.getAttribute("NANO_ROUTE");
        
        if (route != null) {
            // Forward to the backend
            requestOrchestrator.orchestrate(request, response, route);
        } else {
            // If no route, maybe it's actuator or bypassed. Continue chain.
            filterChain.doFilter(servletRequest, servletResponse);
        }
    }
}
