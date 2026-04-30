package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HeaderRateLimitKeyResolverTest {

    private HeaderRateLimitKeyResolver resolver;
    private HttpServletRequest request;
    private RateLimitProperties properties;

    @BeforeEach
    void setUp() {
        resolver = new HeaderRateLimitKeyResolver();
        request = mock(HttpServletRequest.class);
        properties = new RateLimitProperties();
    }

    @Test
    void resolve_throwsExceptionIfResolverArgMissing() {
        properties.setResolverArg(null);
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request, properties));

        properties.setResolverArg("");
        assertThrows(IllegalArgumentException.class, () -> resolver.resolve(request, properties));
    }

    @Test
    void resolve_extractsHeaderValue() {
        properties.setResolverArg("X-API-Key");
        when(request.getHeader("X-API-Key")).thenReturn("secret-token-123");

        String key = resolver.resolve(request, properties);

        assertEquals("secret-token-123", key);
    }

    @Test
    void resolve_fallsBackIfHeaderMissing() {
        properties.setResolverArg("X-API-Key");
        when(request.getHeader("X-API-Key")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("10.0.0.1");

        String key = resolver.resolve(request, properties);

        assertEquals("MISSING_HEADER:10.0.0.1", key);
    }
}
