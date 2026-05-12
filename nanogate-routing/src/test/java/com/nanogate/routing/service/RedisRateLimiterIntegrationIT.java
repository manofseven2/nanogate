package com.nanogate.routing.service;

import com.nanogate.resilience.service.RateLimiterService;
import com.nanogate.resilience.service.RedisRateLimiterService;
import com.nanogate.routing.NanoGateRoutingTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NanoGateRoutingTestApp.class, properties = {
        "nanogate.resilience.rate-limiter.type=redis"
})
@Testcontainers
class RedisRateLimiterIntegrationIT {

    @Container
    @org.springframework.boot.testcontainers.service.connection.ServiceConnection
    static GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    @Autowired
    private RateLimiterService rateLimiterService;

    @Test
    void shouldBeInstanceOfRedisRateLimiter() {
        assertThat(rateLimiterService).isInstanceOf(RedisRateLimiterService.class);
    }

    @Test
    void shouldAllowAndThenBlockRequestsBasedOnRedisLimit() {
        String key = "test-it-key";
        int limit = 10;

        // Warm up the connection to avoid initialization delays affecting the timing
        rateLimiterService.acquirePermission("warmup-key", limit);

        // The first 10 requests should pass
        for (int i = 0; i < limit; i++) {
            assertThat(rateLimiterService.acquirePermission(key, limit)).isTrue();
        }

        // The 11th request should fail, as the bucket is empty and hasn't had 100ms to refill
        assertThat(rateLimiterService.acquirePermission(key, limit)).isFalse();
    }
}
