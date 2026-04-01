package com.nanogate.routing.service;

import com.nanogate.routing.model.HttpClientProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpClient;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

class HttpClientProviderTest {

    private HttpClientProvider provider;

    @BeforeEach
    void setUp() {
        provider = new HttpClientProvider();
    }

    @Test
    void testGetClient_WithSameProperties_ShouldReturnCachedInstance() {
        HttpClientProperties props1 = new HttpClientProperties();
        props1.setConnectTimeout(Duration.ofSeconds(5));

        HttpClientProperties props2 = new HttpClientProperties();
        props2.setConnectTimeout(Duration.ofSeconds(5));

        // They are different objects but equal in value
        assertEquals(props1, props2);

        HttpClient client1 = provider.getClient(props1);
        HttpClient client2 = provider.getClient(props2);

        // Assert that the exact same instance is returned from the cache
        assertSame(client1, client2);
        
        // Basic check to ensure the client was actually created
        assertNotNull(client1);
    }

    @Test
    void testGetClient_WithDifferentProperties_ShouldReturnDifferentInstances() {
        HttpClientProperties props1 = new HttpClientProperties();
        props1.setConnectTimeout(Duration.ofSeconds(5));

        HttpClientProperties props2 = new HttpClientProperties();
        props2.setConnectTimeout(Duration.ofSeconds(10));

        HttpClient client1 = provider.getClient(props1);
        HttpClient client2 = provider.getClient(props2);

        // Assert that different instances are created for different configurations
        assertNotSame(client1, client2);
    }
    
    @Test
    void testGetClient_WithEmptyProperties_ShouldCreateClientWithDefaults() {
        HttpClientProperties emptyProps = new HttpClientProperties();
        
        HttpClient client = provider.getClient(emptyProps);
        
        assertNotNull(client);
        assertTrue(client.connectTimeout().isEmpty());
    }
}
