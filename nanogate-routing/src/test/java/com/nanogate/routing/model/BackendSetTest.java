package com.nanogate.routing.model;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BackendSetTest {

    @Test
    void testGetAndSetProperties() throws Exception {
        BackendSet backendSet = new BackendSet();

        backendSet.setName("backend-set-1");
        assertEquals("backend-set-1", backendSet.getName());

        backendSet.setLoadBalancer("round-robin");
        assertEquals("round-robin", backendSet.getLoadBalancer());

        List<URI> servers = List.of(new URI("http://localhost:8080"));
        backendSet.setServers(servers);
        assertEquals(servers, backendSet.getServers());

        HttpClientProperties properties = new HttpClientProperties();
        backendSet.setHttpClient(properties);
        assertEquals(properties, backendSet.getHttpClient());
    }
}
