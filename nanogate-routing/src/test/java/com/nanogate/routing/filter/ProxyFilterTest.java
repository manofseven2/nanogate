package com.nanogate.routing.filter;

import com.nanogate.routing.model.Route;
import com.nanogate.routing.service.RequestOrchestrator;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProxyFilterTest {

    @Mock
    private RequestOrchestrator requestOrchestrator;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private ProxyFilter proxyFilter;

    @Test
    void doFilter_WithRoute_ShouldOrchestrate() throws Exception {
        Route route = new Route();
        when(request.getAttribute("NANO_ROUTE")).thenReturn(route);

        proxyFilter.doFilter(request, response, filterChain);

        verify(requestOrchestrator).orchestrate(request, response, route);
        verify(filterChain, never()).doFilter(request, response);
    }

    @Test
    void doFilter_WithoutRoute_ShouldProceed() throws Exception {
        when(request.getAttribute("NANO_ROUTE")).thenReturn(null);

        proxyFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(requestOrchestrator);
    }
}
