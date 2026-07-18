package com.nanogate.resilience.service;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterConfig;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedRateLimiterMetrics;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableScheduling
@ConditionalOnProperty(name = "nanogate.resilience.rate-limiter.type", havingValue = "in-memory", matchIfMissing = true)
public class InMemoryRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRateLimiterService.class);
    
    // Expiration time for untouched rate limiters (e.g., 1 hour)
    private static final long EXPIRATION_MILLIS = 3600000;

    private final Map<String, RateLimiterEntry> rateLimiters = new ConcurrentHashMap<>();
    private final RateLimiterRegistry registry = RateLimiterRegistry.ofDefaults();

    public InMemoryRateLimiterService(MeterRegistry meterRegistry) {
        TaggedRateLimiterMetrics
            .ofRateLimiterRegistry(registry)
            .bindTo(meterRegistry);
    }

    @Override
    public boolean acquirePermission(String key, int requestsPerSecond) {
        RateLimiterEntry entry = rateLimiters.computeIfAbsent(key, k -> new RateLimiterEntry(createRateLimiter(k, requestsPerSecond)));
        entry.lastAccessTime = System.currentTimeMillis();
        return entry.rateLimiter.acquirePermission();
    }

    private RateLimiter createRateLimiter(String name, int requestsPerSecond) {
        RateLimiterConfig config = RateLimiterConfig.custom()
                .limitRefreshPeriod(Duration.ofSeconds(1))
                .limitForPeriod(requestsPerSecond)
                .timeoutDuration(Duration.ZERO) // Fail immediately if no permission is available
                .build();
        return registry.rateLimiter(name, config);
    }

    /**
     * Runs periodically to clean up stale rate limiters to prevent memory leaks.
     * Runs every 15 minutes.
     */
    @Scheduled(fixedDelay = 900000)
    public void cleanupStaleLimiters() {
        long now = System.currentTimeMillis();
        int initialSize = rateLimiters.size();

        Iterator<Map.Entry<String, RateLimiterEntry>> iterator = rateLimiters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, RateLimiterEntry> entry = iterator.next();
            if (now - entry.getValue().lastAccessTime > EXPIRATION_MILLIS) {
                registry.remove(entry.getKey());
                iterator.remove();
            }
        }

        int removed = initialSize - rateLimiters.size();
        if (removed > 0) {
            log.debug("Cleaned up {} stale rate limiters. Current active limiters: {}", removed, rateLimiters.size());
        }
    }

    private static class RateLimiterEntry {
        final RateLimiter rateLimiter;
        volatile long lastAccessTime;

        RateLimiterEntry(RateLimiter rateLimiter) {
            this.rateLimiter = rateLimiter;
            this.lastAccessTime = System.currentTimeMillis();
        }
    }
}
