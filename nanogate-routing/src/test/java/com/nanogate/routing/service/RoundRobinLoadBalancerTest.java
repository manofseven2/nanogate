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
class RoundRobinLoadBalancerTest {

    @Mock
    private HealthCheckService healthCheckService;

    @InjectMocks
    private RoundRobinLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        // By default, assume all servers are healthy for existing tests.
        // This is marked as lenient because not all tests will use this stub (e.g., tests with empty server lists).
        lenient().when(healthCheckService.isHealthy(any(URI.class))).thenReturn(true);
    }

    @Test
    void testChooseBackend_WithEmptyServers_ShouldReturnEmpty() {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("empty-set");
        backendSet.setServers(new ArrayList<>());

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isEmpty());
    }

    @Test
    void testChooseBackend_WithNullServers_ShouldReturnEmpty() {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("null-set");
        backendSet.setServers(null);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isEmpty());
    }

    @Test
    void testChooseBackend_SequentialRoundRobin() throws Exception {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("test-set");
        URI uri1 = new URI("http://server1");
        URI uri2 = new URI("http://server2");
        URI uri3 = new URI("http://server3");
        backendSet.setServers(List.of(uri1, uri2, uri3));

        assertEquals(uri1, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri2, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri3, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri1, loadBalancer.chooseBackend(backendSet).get()); // Wraps around
    }

    @Test
    void testChooseBackend_MultipleBackendSets_ShouldNotInterfere() throws Exception {
        BackendSet set1 = new BackendSet();
        set1.setName("set1");
        set1.setServers(List.of(new URI("http://s1-1"), new URI("http://s1-2")));

        BackendSet set2 = new BackendSet();
        set2.setName("set2");
        set2.setServers(List.of(new URI("http://s2-1"), new URI("http://s2-2")));

        assertEquals(new URI("http://s1-1"), loadBalancer.chooseBackend(set1).get());
        assertEquals(new URI("http://s2-1"), loadBalancer.chooseBackend(set2).get());
        assertEquals(new URI("http://s1-2"), loadBalancer.chooseBackend(set1).get());
    }

    @Test
    void testChooseBackend_SkipsUnhealthyServer() throws Exception {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("health-check-set");
        URI uri1 = new URI("http://server1"); // Unhealthy
        URI uri2 = new URI("http://server2"); // Healthy
        URI uri3 = new URI("http://server3"); // Healthy
        backendSet.setServers(List.of(uri1, uri2, uri3));

        // Make only server1 unhealthy
        when(healthCheckService.isHealthy(uri1)).thenReturn(false);
        when(healthCheckService.isHealthy(uri2)).thenReturn(true);
        when(healthCheckService.isHealthy(uri3)).thenReturn(true);

        // The load balancer should skip server1 and go straight to server2, then server3
        assertEquals(uri2, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri3, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri2, loadBalancer.chooseBackend(backendSet).get()); // Wraps around to server2, skipping server1
    }

    @Test
    void testChooseBackend_AllServersUnhealthy_ReturnsEmpty() throws Exception {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("all-unhealthy-set");
        URI uri1 = new URI("http://server1");
        backendSet.setServers(List.of(uri1));

        when(healthCheckService.isHealthy(uri1)).thenReturn(false);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);
        assertTrue(result.isEmpty());
    }
}
