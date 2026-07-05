package com.nanogate.resilience.service;

import net.logstash.logback.argument.StructuredArguments;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.Collections;

@Service
@ConditionalOnProperty(name = "nanogate.resilience.rate-limiter.type", havingValue = "redis")
public class RedisRateLimiterService implements RateLimiterService {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiterService.class);

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> rateLimitScript;

    public RedisRateLimiterService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        
        // Lua script for atomic Token Bucket rate limiting.
        // KEYS[1] = the rate limit bucket key (hash)
        // ARGV[1] = limit (requestsPerSecond - used for both capacity and refill rate)
        // ARGV[2] = current timestamp in milliseconds
        // Returns 1 if allowed, 0 if rate limited.
        String script = 
                "local capacity = tonumber(ARGV[1]) " +
                "local rate = tonumber(ARGV[1]) " +
                "local now = tonumber(ARGV[2]) " +
                "local last_tokens = redis.call('hget', KEYS[1], 'tokens') " +
                "if last_tokens == false then last_tokens = capacity else last_tokens = tonumber(last_tokens) end " +
                "local last_refreshed = redis.call('hget', KEYS[1], 'timestamp') " +
                "if last_refreshed == false then last_refreshed = now else last_refreshed = tonumber(last_refreshed) end " +
                "local delta = math.max(0, (now - last_refreshed) / 1000) " +
                "local filled_tokens = math.min(capacity, last_tokens + (delta * rate)) " +
                "if filled_tokens >= 1 then " +
                "  local new_tokens = filled_tokens - 1 " +
                "  redis.call('hset', KEYS[1], 'tokens', tostring(new_tokens)) " +
                "  redis.call('hset', KEYS[1], 'timestamp', ARGV[2]) " +
                "  redis.call('expire', KEYS[1], 2) " +
                "  return 1 " +
                "else " +
                "  return 0 " +
                "end ";

        this.rateLimitScript = new DefaultRedisScript<>();
        this.rateLimitScript.setScriptText(script);
        this.rateLimitScript.setResultType(Long.class);
    }

    @Override
    public boolean acquirePermission(String key, int requestsPerSecond) {
        try {
            String now = String.valueOf(System.currentTimeMillis());
            Long result = redisTemplate.execute(
                    rateLimitScript,
                    Collections.singletonList("ratelimit:" + key),
                    String.valueOf(requestsPerSecond),
                    now
            );
            log.info("RedisRateLimiterService acquirePermission result for {}: {}", key, result);
            return result != null && result == 1L;
        } catch (Exception e) {
            log.warn("Redis rate limiter failed for key: {}. Allowing request. Error: {}", key, e.getMessage(),
                    StructuredArguments.kv("rateLimitKey", key),
                    StructuredArguments.kv("error_type", "RedisError"));
            // Fail open: allow traffic if Redis goes down
            return true;
        }
    }
}
