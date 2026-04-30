package com.nanogate.resilience.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryRateLimiterServiceTest {

    private InMemoryRateLimiterService service;

    @BeforeEach
    void setUp() {
        service = new InMemoryRateLimiterService();
    }

    @Test
    void acquirePermission_allowsUpToLimit() {
        String key = "test-key";
        int limit = 2;

        assertTrue(service.acquirePermission(key, limit));
        assertTrue(service.acquirePermission(key, limit));
    }

    @Test
    void acquirePermission_blocksAfterLimit() {
        String key = "test-key-2";
        int limit = 1;

        assertTrue(service.acquirePermission(key, limit));
        assertFalse(service.acquirePermission(key, limit));
    }

    @Test
    void acquirePermission_isolatesKeys() {
        String key1 = "key-1";
        String key2 = "key-2";
        int limit = 1;

        assertTrue(service.acquirePermission(key1, limit));
        assertTrue(service.acquirePermission(key2, limit));
        
        assertFalse(service.acquirePermission(key1, limit));
        assertFalse(service.acquirePermission(key2, limit));
    }
}
