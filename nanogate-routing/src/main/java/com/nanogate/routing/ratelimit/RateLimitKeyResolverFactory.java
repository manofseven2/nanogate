package com.nanogate.routing.ratelimit;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory to retrieve the correct RateLimitKeyResolver based on its name.
 */
@Component
public class RateLimitKeyResolverFactory {

    private final Map<String, RateLimitKeyResolver> resolverMap;
    private final RateLimitKeyResolver defaultResolver;

    public RateLimitKeyResolverFactory(List<RateLimitKeyResolver> resolvers, IpRateLimitKeyResolver defaultResolver) {
        this.resolverMap = resolvers.stream()
                .collect(Collectors.toMap(
                        resolver -> resolver.name().toUpperCase(),
                        Function.identity()
                ));
        this.defaultResolver = defaultResolver;
    }

    /**
     * Gets the resolver by name.
     *
     * @param name The name of the resolver (e.g., "IP", "HEADER").
     * @return The corresponding resolver, or the default IP resolver if not found.
     */
    public RateLimitKeyResolver getResolver(String name) {
        if (name == null || name.trim().isEmpty()) {
            return defaultResolver;
        }
        return resolverMap.getOrDefault(name.trim().toUpperCase(), defaultResolver);
    }
}
