package com.nanogate.resilience.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RedisRateLimiterServiceTest {

    private StringRedisTemplate redisTemplate;
    private RedisRateLimiterService service;

    @BeforeEach
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        service = new RedisRateLimiterService(redisTemplate);
    }

    @Test
    void acquirePermission_allowedWhenScriptReturnsOne() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                anyString(),
                anyString()
        )).thenReturn(1L);

        boolean result = service.acquirePermission("test-key", 5);
        assertTrue(result, "Expected acquirePermission to be true when script returns 1L");
    }

    @Test
    void acquirePermission_deniedWhenScriptReturnsZero() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                anyString(),
                anyString()
        )).thenReturn(0L);

        boolean result = service.acquirePermission("test-key", 5);
        assertFalse(result, "Expected acquirePermission to be false when script returns 0L");
    }

    @Test
    void acquirePermission_failsOpenWhenExceptionThrown() {
        when(redisTemplate.execute(
                ArgumentMatchers.<RedisScript<Long>>any(),
                ArgumentMatchers.<List<String>>any(),
                anyString(),
                anyString()
        )).thenThrow(new RuntimeException("Redis connection error"));

        // Should log error and return true (fail-open)
        boolean result = service.acquirePermission("test-key", 5);
        assertTrue(result, "Expected acquirePermission to be true (fail-open) when Redis throws an exception");
    }
}
