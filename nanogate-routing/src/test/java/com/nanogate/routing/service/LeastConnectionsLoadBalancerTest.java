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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeastConnectionsLoadBalancerTest {

    @Mock
    private ActiveConnectionTracker tracker;

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
    void testChooseBackend_OptimizesWhenServerHasZeroConnections() {
        // Since server1 has 0 connections, the loop should break early and pick it.
        // It shouldn't even check server2 or server3 if they have 0 too.
        when(tracker.getActiveConnections(server1)).thenReturn(0);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isPresent());
        assertEquals(server1, result.get());
    }

    @Test
    void testChooseBackend_WithEqualConnections_SelectsFirstInList() {
        when(tracker.getActiveConnections(server1)).thenReturn(5);
        when(tracker.getActiveConnections(server2)).thenReturn(5);
        when(tracker.getActiveConnections(server3)).thenReturn(5);

        Optional<URI> result = loadBalancer.chooseBackend(backendSet);

        assertTrue(result.isPresent());
        // Since they are all equal (and not 0), it should just return the first one checked
        assertEquals(server1, result.get());
    }
}
