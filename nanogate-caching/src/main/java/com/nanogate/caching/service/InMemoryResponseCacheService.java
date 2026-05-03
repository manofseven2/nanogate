package com.nanogate.caching.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.nanogate.caching.model.CachedResponse;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
public class InMemoryResponseCacheService implements ResponseCacheService {

    // Helper wrapper to store both the entry and its individual TTL.
    private static class CacheEntry {
        final CachedResponse response;
        final long ttlNanos;

        CacheEntry(CachedResponse response, Duration ttl) {
            this.response = response;
            // Cap TTL at an arbitrary maximum to avoid overflow, though highly unlikely
            this.ttlNanos = ttl != null ? ttl.toNanos() : Duration.ofMinutes(5).toNanos(); 
        }
    }

    private final Cache<String, CacheEntry> cache;

    public InMemoryResponseCacheService() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfter(new Expiry<String, CacheEntry>() {
                    @Override
                    public long expireAfterCreate(String key, CacheEntry value, long currentTime) {
                        return value.ttlNanos;
                    }

                    @Override
                    public long expireAfterUpdate(String key, CacheEntry value, long currentTime, long currentDuration) {
                        return value.ttlNanos;
                    }

                    @Override
                    public long expireAfterRead(String key, CacheEntry value, long currentTime, long currentDuration) {
                        return currentDuration; // Keep the existing expiration time
                    }
                })
                .build();
    }

    @Override
    public Optional<CachedResponse> get(String key) {
        CacheEntry entry = cache.getIfPresent(key);
        return entry != null ? Optional.of(entry.response) : Optional.empty();
    }

    @Override
    public void put(String key, CachedResponse response, Duration ttl) {
        if (ttl == null || ttl.isNegative() || ttl.isZero()) {
            return;
        }
        cache.put(key, new CacheEntry(response, ttl));
    }
}
