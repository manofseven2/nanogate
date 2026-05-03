package com.nanogate.routing.service;

import com.nanogate.routing.model.HttpClientProperties;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.util.concurrent.ConcurrentHashMap;

/**
 * A provider and cache for HttpClient instances.
 * It creates and caches clients based on their configuration to avoid the
 * overhead
 * of creating a new client for every request, while ensuring thread safety.
 */
@Service
public class HttpClientProvider {

    private final ConcurrentHashMap<HttpClientProperties, HttpClient> clientCache = new ConcurrentHashMap<>();

    /**
     * Gets a cached HttpClient for the given properties, or creates a new one if
     * not present.
     *
     * @param properties The desired properties for the HttpClient.
     * @return A thread-safe, potentially cached HttpClient instance.
     */
    public HttpClient getClient(HttpClientProperties properties) {
        // computeIfAbsent ensures that the client is only created once for a given set
        // of properties.
        return clientCache.computeIfAbsent(properties, this::createClient);
    }

    private HttpClient createClient(HttpClientProperties properties) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1); // Default, can be made configurable

        if (properties.getConnectTimeout() != null) {
            builder.connectTimeout(properties.getConnectTimeout());
        }
        // Note: Java's HttpClient doesn't have a direct "responseTimeout" like some
        // other clients.
        // The timeout is set on a per-request basis. We will handle this in the
        // RequestProxy.

        return builder.build();
    }
}