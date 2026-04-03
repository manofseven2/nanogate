# ADR 003: Granular Health Checking & Isolation

## Context

A core responsibility of an API Gateway is preventing traffic from reaching unhealthy backend servers. A naïve implementation relies solely on load balancer round-robin algorithms, but if a pool contains 5 servers and 1 crashes, 20% of all user requests will randomly fail with `502 Bad Gateway` errors.

To solve this, the gateway needs a mechanism to detect and isolate failures. A common but flawed approach is tracking health at the `BackendSet` (pool) level:
*   **The Problem:** If one instance in a `BackendSet` fails, you cannot mark the entire `BackendSet` as `DOWN`, otherwise you take down the 4 perfectly healthy instances and break the API for everyone. 
*   **The Problem:** If you don't mark it `DOWN`, the load balancer continues to blindly throw traffic at the dead instance.

## Decision

We will track health at the **individual URI (Instance) Level**, utilizing a dual-pronged approach of **Active Background Pinging** and **Passive Real-time Circuit Breaking**.

## Reasoning

To achieve true resiliency and zero-downtime routing, NanoGate implements the following architecture:

1.  **The Central Health Registry:** A thread-safe, in-memory `ConcurrentHashMap<URI, Boolean>` maintains the UP/DOWN status of every specific downstream server URI across all pools.
2.  **Active Health Checking (The Daemon):** A scheduled background worker runs every *X* seconds, sending HTTP GET requests to the `/health` endpoint of every URI in the configuration. It updates the `HealthRegistry`.
    *   *Why:* Proactive detection. The gateway knows a server is down *before* a client tries to reach it.
3.  **Load Balancer Integration:** The `RoundRobin` and `LeastConnections` load balancers are modified to consult the `HealthRegistry` before selecting a server. If a URI is marked `DOWN`, it is immediately skipped, and the next healthy server is calculated.
4.  **Passive Health Checking (Resilience4j Circuit Breakers):** Active pinging is too slow to catch a server that crashes *between* the 10-second checks. NanoGate wraps the physical `HttpClient.send()` call to specific URIs with a Resilience4j Circuit Breaker.
    *   *Why:* Reactive detection. If a real user request fails with a connection timeout or 5xx error, the Circuit Breaker records it. If failures cross a threshold (e.g., 3 in a row), the circuit "opens" and instantly cuts off traffic to that specific URI, acting as a real-time safety net.
5.  **Actuator Visibility:** The precise state of the `HealthRegistry` and Circuit Breakers is exposed via Spring Boot Actuator (`/actuator/gateway-health`), giving platform engineers full visibility into which specific nodes inside a cluster are currently excluded from the rotation.

## Consequences

*   **Zero-Downtime Reliability:** Intermittent failures are eliminated. Faulty nodes are seamlessly isolated, and traffic is redirected to healthy peers within milliseconds.
*   **Granular Observability:** Operations teams can see exactly which physical instance is causing problems, rather than just knowing a generic "pool" is acting up.
*   **Complexity:** This introduces state management (the `HealthRegistry`) and necessitates careful concurrency control (which is handled naturally by `ConcurrentHashMap` and Resilience4j).