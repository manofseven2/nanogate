package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class IpRateLimitKeyResolverTest {

    private IpRateLimitKeyResolver resolver;
    private HttpServletRequest request;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        resolver = new IpRateLimitKeyResolver("X-Forwarded-For");
        request = mock(HttpServletRequest.class);
        properties = new RateLimitProperties();
    }

    @Test
    void resolve_usesHeaderIfPresent() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100");

        String key = resolver.resolve(request, properties);

        assertEquals("192.168.1.100", key);
    }

    @Test
    void resolve_usesFirstIpFromCommaSeparatedHeader() {
        when(request.getHeader("X-Forwarded-For")).thenReturn("192.168.1.100, 10.0.0.1, 127.0.0.1");

        String key = resolver.resolve(request, properties);

        assertEquals("192.168.1.100", key);
    }

    @Test
    void resolve_fallsBackToRemoteAddrIfHeaderMissing() {
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.5");

        String key = resolver.resolve(request, properties);

        assertEquals("10.0.0.5", key);
    }
}
