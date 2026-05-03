package com.nanogate.caching.service;

import com.nanogate.caching.model.CachedResponse;

import java.time.Duration;
import java.util.Optional;

public interface ResponseCacheService {
    Optional<CachedResponse> get(String key);
    void put(String key, CachedResponse response, Duration ttl);
}
