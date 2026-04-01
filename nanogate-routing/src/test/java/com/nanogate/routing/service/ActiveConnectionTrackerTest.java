package com.nanogate.routing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ActiveConnectionTrackerTest {

    private ActiveConnectionTracker tracker;
    private URI server1;
    private URI server2;

    @BeforeEach
    void setUp() throws Exception {
        tracker = new ActiveConnectionTracker();
        server1 = new URI("http://backend-1");
        server2 = new URI("http://backend-2");
    }

    @Test
    void testInitialCount_IsZero() {
        assertEquals(0, tracker.getActiveConnections(server1));
    }

    @Test
    void testIncrement_IncreasesCount() {
        tracker.increment(server1);
        tracker.increment(server1);
        assertEquals(2, tracker.getActiveConnections(server1));
        
        // Other servers shouldn't be affected
        assertEquals(0, tracker.getActiveConnections(server2));
    }

    @Test
    void testDecrement_DecreasesCount() {
        tracker.increment(server1);
        tracker.increment(server1);
        
        tracker.decrement(server1);
        assertEquals(1, tracker.getActiveConnections(server1));
    }

    @Test
    void testDecrement_NeverDropsBelowZero() {
        tracker.increment(server1);
        
        tracker.decrement(server1);
        tracker.decrement(server1); // Should stay at 0
        tracker.decrement(server1); // Should stay at 0
        
        assertEquals(0, tracker.getActiveConnections(server1));
    }

    @Test
    void testNullUri_IsHandledGracefully() {
        tracker.increment(null);
        tracker.decrement(null);
        assertEquals(0, tracker.getActiveConnections(null));
    }

    @Test
    void testConcurrentAccess_IsThreadSafe() throws Exception {
        int threadCount = 100;
        int operationsPerThread = 1000;
        
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    for (int j = 0; j < operationsPerThread; j++) {
                        tracker.increment(server1);
                        // Simulate some work
                        Thread.yield();
                        tracker.decrement(server1);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Unleash all threads at once
        doneLatch.await(5, TimeUnit.SECONDS);
        executor.shutdown();

        // After exactly N increments and N decrements, the count should be perfectly zero
        assertEquals(0, tracker.getActiveConnections(server1));
    }
}
