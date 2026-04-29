package com.nanogate.security.filter;

import com.nanogate.security.SecurityConstants;
import com.nanogate.security.service.IpSecurityService;
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
@Order(10) // Runs after RouteResolutionFilter (Order 1) but before ProxyFilter (Order 100)
public class IpSetFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(IpSetFilter.class);

    private final IpSecurityService ipSecurityService;
    private final String ipHeader;

    public IpSetFilter(IpSecurityService ipSecurityService, 
                       @org.springframework.beans.factory.annotation.Value("${nanogate.security.ip-header:X-Forwarded-For}") String ipHeader) {
        this.ipSecurityService = ipSecurityService;
        this.ipHeader = ipHeader;
    }

    @Override
    public void doFilter(ServletRequest servletRequest, ServletResponse servletResponse, FilterChain filterChain)
            throws IOException, ServletException {

        String ipSetName = (String) servletRequest.getAttribute(SecurityConstants.IP_SET_ATTRIBUTE);

        if (ipSetName != null) {
            HttpServletRequest request = (HttpServletRequest) servletRequest;
            String clientIp = request.getHeader(ipHeader);
            if (clientIp == null || clientIp.isEmpty()) {
                clientIp = request.getRemoteAddr();
            } else {
                clientIp = clientIp.split(",")[0].trim();
            }

            if (!ipSecurityService.isAllowed(ipSetName, clientIp)) {
                log.warn("Access denied for IP {} matching IpSet '{}'", clientIp, ipSetName);
                HttpServletResponse response = (HttpServletResponse) servletResponse;
                response.sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied by IP Policy");
                return; // Halt the chain
            }
        }

        filterChain.doFilter(servletRequest, servletResponse);
    }
}
