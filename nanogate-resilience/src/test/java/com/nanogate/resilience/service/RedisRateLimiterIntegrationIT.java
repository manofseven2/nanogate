package com.nanogate.resilience.service;

import com.nanogate.resilience.NanoGateResilienceTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NanoGateResilienceTestApp.class, properties = {
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
    void shouldAccuratelyRateLimitAcrossParallelExecutionThreads() throws InterruptedException {
        String key = "parallel-test-key";
        int limit = 10;
        int totalRequests = 50;

        // Warm up the connection
        rateLimiterService.acquirePermission("warmup-key", limit);

        ExecutorService executor = Executors.newFixedThreadPool(totalRequests);
        CountDownLatch latch = new CountDownLatch(totalRequests);
        AtomicInteger successfulRequests = new AtomicInteger(0);
        AtomicInteger failedRequests = new AtomicInteger(0);

        for (int i = 0; i < totalRequests; i++) {
            executor.submit(() -> {
                try {
                    boolean acquired = rateLimiterService.acquirePermission(key, limit);
                    if (acquired) {
                        successfulRequests.incrementAndGet();
                    } else {
                        failedRequests.incrementAndGet();
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await();
        executor.shutdown();

        // Exactly 'limit' requests should have succeeded, the rest should have failed
        assertThat(successfulRequests.get()).isEqualTo(limit);
        assertThat(failedRequests.get()).isEqualTo(totalRequests - limit);
    }
}
