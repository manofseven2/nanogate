# Bonus Features & Future Investigations

This document captures advanced architectural concepts, patterns, and potential features for NanoGate that fall outside the current implementation phases but warrant further investigation and eventual implementation.

---

## 1. Traffic Mirroring (Shadow Routing / Dark Launching)

### Context & Feasibility
Traffic mirroring allows the duplication of live production traffic to a staging or "v2" service without impacting the actual user's response or experience. This is a highly advanced, enterprise-grade pattern used for testing new deployments under real load before directing actual user traffic to them.

Implementing this in NanoGate is highly feasible and elegant due to our technology stack (Java 21 + Virtual Threads).

### The Mechanism
When a `Route` is configured with a `mirror-backend-set`:
1.  **Primary Request:** The gateway forwards the request to the primary backend and returns the response to the client (standard synchronous flow).
2.  **Shadow Request:** The gateway fires a "fire-and-forget" duplicate request to the mirror backend and completely ignores its response.

### Why NanoGate Excels at This (The Virtual Thread Advantage)
In older Java frameworks, executing "fire-and-forget" shadow requests required allocating expensive physical OS threads from a thread pool. If the shadow backend was slow or unresponsive, the gateway risked exhausting its thread pool and crashing the primary routing engine just to support mirroring.

With **Virtual Threads**, NanoGate can simply spawn a new, virtually free thread for the shadow request:

```java
if (route.hasMirror()) {
    // Fire and forget on a cheap Virtual Thread
    Thread.startVirtualThread(() -> {
        try {
            sendShadowRequest(requestBuilder.build(), mirrorUri);
        } catch (Exception e) {
            // Log it, but never impact the main user thread
            log.warn("Shadow routing failed"); 
        }
    });
}
// Main thread continues routing to the primary backend...
```

The overhead is nearly zero. If the shadow backend is slow, the virtual thread simply sleeps in RAM and eventually dies, with absolutely no impact on the primary user's latency.

### The "Body" Caveat
An HTTP request body (`InputStream`) can generally only be read once. To mirror `POST`/`PUT` requests, NanoGate would have to buffer the request body into memory (e.g., a `byte[]`) so it can be sent to both the primary and shadow servers. 
*   **Implication:** Mirroring is perfect for standard JSON APIs, but we would need to explicitly disable or strictly limit mirroring for massive file uploads to protect our OOM (Out Of Memory) resilient architecture.

### NanoGate vs. Industry Alternatives

**A. Custom "Middle Ingestion Services" (Message Brokers)**
*   Some organizations build complex architectures involving publishing all traffic to Kafka/RabbitMQ and having worker services read it to send to multiple systems.
*   **NanoGate's Strength:** This is a heavy, complex, and expensive architecture. NanoGate achieves the same result in-memory, at the edge, instantly. No message brokers, no extra databases. It's a simple YAML configuration (`mirror-to: v2-cluster`).

**B. Service Meshes (Envoy / Istio)**
*   Envoy and Istio are the current industry standard for traffic mirroring, running as C++ "sidecars".
*   **NanoGate's Strength (Developer Experience):** Configuring Istio for shadow routing requires deep knowledge of complex Kubernetes CRDs and Envoy YAML. Furthermore, if a platform team wants custom mirroring logic (e.g., "Only mirror traffic if the user's ID ends in 5"), doing so in Envoy requires writing custom WebAssembly (Wasm) plugins or complex Lua scripts. NanoGate is a standard Java Spring Boot application; developers can add that custom logic with five lines of standard Java code, making it infinitely more maintainable for Java/C# centric teams.