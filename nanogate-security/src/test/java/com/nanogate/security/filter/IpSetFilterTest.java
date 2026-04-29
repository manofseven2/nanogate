package com.nanogate.security.filter;

import com.nanogate.security.SecurityConstants;
import com.nanogate.security.service.IpSecurityService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IpSetFilterTest {

    @Mock
    private IpSecurityService ipSecurityService;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;
    @Mock
    private FilterChain filterChain;

    private IpSetFilter ipSetFilter;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ipSetFilter = new IpSetFilter(ipSecurityService, "X-Forwarded-For");
    }

    @Test
    void doFilter_NoIpSet_ShouldProceed() throws Exception {
        when(request.getAttribute(SecurityConstants.IP_SET_ATTRIBUTE)).thenReturn(null);

        ipSetFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
        verifyNoInteractions(ipSecurityService);
    }

    @Test
    void doFilter_IpAllowed_ShouldProceed() throws Exception {
        when(request.getAttribute(SecurityConstants.IP_SET_ATTRIBUTE)).thenReturn("vpn-only");
        when(request.getRemoteAddr()).thenReturn("192.168.1.50");
        when(ipSecurityService.isAllowed("vpn-only", "192.168.1.50")).thenReturn(true);

        ipSetFilter.doFilter(request, response, filterChain);

        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_IpBlocked_ShouldReturn403() throws Exception {
        when(request.getAttribute(SecurityConstants.IP_SET_ATTRIBUTE)).thenReturn("vpn-only");
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");
        when(ipSecurityService.isAllowed("vpn-only", "10.0.0.1")).thenReturn(false);

        ipSetFilter.doFilter(request, response, filterChain);

        verify(response).sendError(HttpServletResponse.SC_FORBIDDEN, "Access Denied by IP Policy");
        verify(filterChain, never()).doFilter(request, response);
    }
}
