package com.nanogate.routing.service;

import com.nanogate.routing.model.BackendSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class RoundRobinLoadBalancerTest {

    private RoundRobinLoadBalancer loadBalancer;

    @BeforeEach
    void setUp() {
        loadBalancer = new RoundRobinLoadBalancer();
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

        // The first call gets the index 1 (because it initializes at 0 and increments before modulo)
        // This is a quirk of the current implementation, but we test the behavior as is.
        assertEquals(uri2, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri3, loadBalancer.chooseBackend(backendSet).get());
        assertEquals(uri1, loadBalancer.chooseBackend(backendSet).get()); // Wraps around
        assertEquals(uri2, loadBalancer.chooseBackend(backendSet).get());
    }

    @Test
    void testChooseBackend_MultipleBackendSets_ShouldNotInterfere() throws Exception {
        BackendSet set1 = new BackendSet();
        set1.setName("set1");
        set1.setServers(List.of(new URI("http://s1-1"), new URI("http://s1-2")));

        BackendSet set2 = new BackendSet();
        set2.setName("set2");
        set2.setServers(List.of(new URI("http://s2-1"), new URI("http://s2-2")));

        // Set 1 round robin
        assertEquals(new URI("http://s1-2"), loadBalancer.chooseBackend(set1).get());
        
        // Set 2 round robin (should start fresh, not affected by set1's counter)
        assertEquals(new URI("http://s2-2"), loadBalancer.chooseBackend(set2).get());

        // Back to Set 1
        assertEquals(new URI("http://s1-1"), loadBalancer.chooseBackend(set1).get());
    }

    @Test
    void testChooseBackend_ConcurrentAccess() throws Exception {
        int threadCount = 100;
        int requestsPerThread = 1000;
        int totalRequests = threadCount * requestsPerThread;
        
        BackendSet backendSet = new BackendSet();
        backendSet.setName("concurrent-set");
        URI uri1 = new URI("http://server1");
        URI uri2 = new URI("http://server2");
        backendSet.setServers(List.of(uri1, uri2));

        AtomicInteger uri1Count = new AtomicInteger(0);
        AtomicInteger uri2Count = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    for (int j = 0; j < requestsPerThread; j++) {
                        Optional<URI> selected = loadBalancer.chooseBackend(backendSet);
                        if (selected.isPresent()) {
                            if (selected.get().equals(uri1)) {
                                uri1Count.incrementAndGet();
                            } else if (selected.get().equals(uri2)) {
                                uri2Count.incrementAndGet();
                            }
                        }
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // In a perfectly fair round-robin over an even number of requests, the counts should be exactly equal
        assertEquals(totalRequests / 2, uri1Count.get());
        assertEquals(totalRequests / 2, uri2Count.get());
    }
}
