package com.nanogate.routing.ratelimit;

import com.nanogate.routing.model.RateLimitProperties;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class GlobalRateLimitKeyResolverTest {

    @Test
    void testResolveReturnsAll() {
        GlobalRateLimitKeyResolver resolver = new GlobalRateLimitKeyResolver();
        assertEquals("GLOBAL", resolver.name());
        assertEquals("ALL", resolver.resolve(mock(HttpServletRequest.class), new RateLimitProperties()));
    }
}
