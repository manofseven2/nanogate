# Epic: NanoGate Phase 2 - Resiliency & Advanced Routing

**Epic Goal:** Transform the foundational routing engine into a production-ready, fault-tolerant gateway that natively handles large streams, shapes HTTP traffic, gracefully isolates unhealthy backend instances, and provides real-time visibility into cluster health and routing metrics.

---

## Task 1: Implement Streaming Proxy (TCP Backpressure)
*   **Goal:** Replace the memory-heavy byte array proxying with a highly efficient, chunked streaming model.
*   **Definition:**
    1.  Refactor `RequestProxy.java` to use `HttpResponse.BodyHandlers.ofInputStream()`.
    2.  Stream the backend response directly to the `HttpServletResponse` output stream using Java's optimized `InputStream.transferTo(OutputStream)` method.
    3.  Ensure HTTP headers and status codes are flushed to the client before body streaming begins.
*   **Reasoning:** This leverages OS-level TCP backpressure. If a client is slow, the virtual thread blocks, naturally pausing the backend read and preventing `OutOfMemoryError`s without complex reactive frameworks. It also natively enables Server-Sent Events (SSE) and large file downloads.

## Task 2: Active Health Checking Daemon & Startup Warnings
*   **Goal:** Implement a background worker that proactively pings backend instances, and warn developers about unmonitored services.
*   **Definition:**
    1.  Create a `HealthCheckProperties` model (e.g., `path`, `interval`, `timeout`) and add it to the `BackendSet` configuration.
    2.  Create a thread-safe `HealthRegistry` (e.g., `ConcurrentHashMap<URI, Boolean>`) to store the UP/DOWN status of every individual backend URI.
    3.  Create a `@Scheduled` background task (`ActiveHealthChecker`) that periodically iterates through all configured URIs, sends an HTTP GET request to their health path, and updates the `HealthRegistry`.
    4.  If a `BackendSet` does not have a `health-check` block, its servers will not be monitored and will be assumed healthy by default.
    5.  Upon application startup, log a clear `WARN` message for every `BackendSet` that is missing a `health-check` configuration.

## Task 3: Load Balancer Health Integration
*   **Goal:** Ensure the routing engine completely bypasses unhealthy backend instances.
*   **Definition:**
    1.  Inject the `HealthCheckService` into the `RoundRobinLoadBalancer` and `LeastConnectionsLoadBalancer`.
    2.  Modify the `chooseBackend` logic: Before returning a URI, verify that `healthCheckService.isHealthy(uri)` is true.
    3.  If the chosen URI is `DOWN`, the load balancer must skip it and calculate the next available healthy server in the pool.
    4.  If *all* servers in a `BackendSet` are `DOWN`, return a `503 Service Unavailable` immediately.

## Task 4: Hierarchical Actuator Health Indicator
*   **Goal:** Expose a structured, hierarchical health status for all backend sets and their individual servers, which is the industry standard for observability.
*   **Definition:**
    1.  Add the `spring-boot-starter-actuator` dependency.
    2.  Implement a custom `NanoGateHealthIndicator` that provides a nested JSON output.
    3.  The health indicator will group servers by their `BackendSet`.
    4.  Each `BackendSet` will have its own `status` (`UP` if at least one server is healthy/unmonitored, `DOWN` otherwise).
    5.  Each server within a `BackendSet` will have its individual status (`UP`, `DOWN`, or `UNMONITORED`).

## Task 5: Passive Health Checking via Circuit Breakers
*   **Goal:** Implement a reactive safety net that detects backend failures on live traffic and instantly isolates failing servers, complementing the proactive active health checks.
*   **Definition:**
    1.  Integrate `resilience4j-circuitbreaker` into the `nanogate-resilience` module.
    2.  Wrap the `HttpClient.send()` execution inside `RequestProxy` with a Circuit Breaker, keyed by the target `URI`.
    3.  The Circuit Breaker will monitor for connection errors and 5xx responses on real user requests.
    4.  If the failure rate for a specific server exceeds a threshold, the circuit will "open," causing subsequent requests to that server to fail instantly for a configured duration.
    5.  When a `CallNotPermittedException` is caught (indicating an open circuit), the `RoutingFilter` will immediately attempt to retry the request on a *different* healthy server via the Load Balancer.

## Task 6: Header Transformation & Manipulation
*   **Goal:** Allow administrators to shape HTTP traffic by adding, removing, or overwriting headers.
*   **Definition:**
    1.  Extend the `Route` configuration model to include properties for `addRequestHeaders`, `removeRequestHeaders`, `addResponseHeaders`, and `removeResponseHeaders`.
    2.  Update `RequestProxy` to apply these rules dynamically during the `HttpRequest` construction and before writing the `HttpServletResponse`.

## Task 7: URL Rewriting & Prefix Stripping
*   **Goal:** Allow NanoGate to expose clean external APIs while routing to complex internal microservice paths.
*   **Definition:**
    1.  Add `stripPrefix` (integer) and `rewritePath` (regex string) properties to the `Route` model.
    2.  Update `RequestProxy` to calculate the final `targetUri` based on these rules before forwarding the request (e.g., stripping `/api/users` down to `/` for the downstream service).

## Task 8: Core RED Metrics Instrumentation (Prometheus)
*   **Goal:** Instrument the gateway's core routing and resiliency components with Micrometer to expose essential RED (Rate, Errors, Duration) metrics.
*   **Definition:**
    1.  Add the `micrometer-registry-prometheus` dependency.
    2.  Instrument the `RoutingFilter` (or `RequestProxy`) using a Micrometer `Timer` to record the latency and throughput of every request, tagged by the `Route ID` and HTTP status code.
    3.  Configure Resilience4j's Micrometer integration to automatically expose the state of every `CircuitBreaker`.
    4.  Expose the `/actuator/prometheus` endpoint.

## Task 9: Comprehensive Integration Testing
*   **Goal:** Prove that the new complex streaming, resiliency, and routing mechanisms work under realistic load.
*   **Definition:**
    1.  Write an `*IT.java` test that uses WireMock to return a massive (e.g., 50MB) payload to verify the streaming proxy (Task 1) doesn't cause an OOM error.
    2.  Write an `*IT.java` test that configures WireMock to return `500 Server Error` consistently to verify the Circuit Breaker (Task 5) opens and traffic is rerouted.
    3.  Write tests validating Header Transformation (Task 6) and URL Rewriting (Task 7) rules.
