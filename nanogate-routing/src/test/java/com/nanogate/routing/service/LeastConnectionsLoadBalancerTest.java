package com.nanogate.routing.service;

import com.nanogate.routing.model.BackendSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeastConnectionsLoadBalancerTest {

    @Mock
    private ActiveConnectionTracker tracker;

    @Mock
    private HealthCheckService healthCheckService;

    @InjectMocks
    private LeastConnectionsLoadBalancer loadBalancer;

    private BackendSet backendSet;
    private URI server1;
    private URI server2;
    private URI server3;

    @BeforeEach
    void setUp() throws Exception {
        server1 = new URI("http://backend-1");
        server2 = new URI("http://backend-2");
        server3 = new URI("http://backend-3");

        backendSet = new BackendSet();
        backendSet.setName("test-set");
        backendSet.setServers(List.of(server1, server2, server3));

        // By default, assume all servers are healthy for existing tests.
        // This is marked as lenient because not all tests will use this stub (e.g., tests with empty server lists).
        lenient().when(healthCheckService.isHealthy(any(URI.class))).thenReturn(true);
    }

    @Test
    void testChooseBackend_WithEmptyServers_ShouldReturnEmpty() {
        backendSet.setServers(new ArrayList<>());
        Optional<URI> result = loadBalancer.chooseBackend(backendSet);
        assertTrue(result.isEmpty());
    }

    @Test
    void testChooseBackend_WithNullServers_ShouldReturnEmpty() {
        backendSet.setServers(null);
        Optional<URI> result = loadBalancer.chooseBackend(backendSet);
        assertTrue(result.isEmpty());
    }

    @Test
    void testChooseBackend_SelectsServerWithFewestConnections() {
        // server1 is busy, server2 is least busy, server3 is somewhat busy
        when(tracker.getActiveConnections(server1)).thenReturn(10);
        when(tracker.getActiveConnections(server2)).thenReturn(2);
        when(tracker.getActiveConnections(server3)).thenReturn(5);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isPresent());
        assertEquals(server2, result.get());
    }

    @Test
    void testChooseBackend_SkipsUnhealthyServer() {
        // server2 is the least busy, but it's unhealthy, so it should be skipped.
        when(tracker.getActiveConnections(server1)).thenReturn(10);
        // No stub for server2's connections is needed, as it will be filtered out first.
        when(tracker.getActiveConnections(server3)).thenReturn(5);

        when(healthCheckService.isHealthy(server1)).thenReturn(true);
        when(healthCheckService.isHealthy(server2)).thenReturn(false); // server2 is unhealthy
        when(healthCheckService.isHealthy(server3)).thenReturn(true);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isPresent());
        // It should skip server2 and pick server3, which is the next least busy healthy server
        assertEquals(server3, result.get());
    }

    @Test
    void testChooseBackend_AllServersUnhealthy_ReturnsEmpty() {
        when(healthCheckService.isHealthy(server1)).thenReturn(false);
        when(healthCheckService.isHealthy(server2)).thenReturn(false);
        when(healthCheckService.isHealthy(server3)).thenReturn(false);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);
        assertTrue(result.isEmpty());
    }
}
