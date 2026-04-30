package com.nanogate.routing.ratelimit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RateLimitKeyResolverFactoryTest {

    private RateLimitKeyResolverFactory factory;
    private IpRateLimitKeyResolver defaultResolver;

    @BeforeEach
    void setUp() {
        defaultResolver = new IpRateLimitKeyResolver("X-Forwarded-For");
        HeaderRateLimitKeyResolver headerResolver = new HeaderRateLimitKeyResolver();
        
        factory = new RateLimitKeyResolverFactory(
                List.of(defaultResolver, headerResolver),
                defaultResolver
        );
    }

    @Test
    void getResolver_returnsCorrectResolverCaseInsensitive() {
        assertTrue(factory.getResolver("HEADER") instanceof HeaderRateLimitKeyResolver);
        assertTrue(factory.getResolver("header") instanceof HeaderRateLimitKeyResolver);
        assertTrue(factory.getResolver("IP") instanceof IpRateLimitKeyResolver);
    }

    @Test
    void getResolver_fallsBackToDefaultIfNotFound() {
        RateLimitKeyResolver resolver = factory.getResolver("UNKNOWN");
        assertEquals(defaultResolver, resolver);
    }

    @Test
    void getResolver_fallsBackToDefaultIfNullOrEmpty() {
        assertEquals(defaultResolver, factory.getResolver(null));
        assertEquals(defaultResolver, factory.getResolver(""));
        assertEquals(defaultResolver, factory.getResolver("   "));
    }
}
