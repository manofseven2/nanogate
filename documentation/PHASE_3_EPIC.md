# Epic: NanoGate Phase 3 - Cross-Cutting Concerns

**Epic Goal:** Introduce the necessary API management features that decouple clients from backend complexities, focusing on threat protection, rate limiting, and response optimization.

---

## Task 1: Zero-Overhead IP Filtering (IpSets)
*   **Goal:** Provide reusable, highly efficient IP allowlisting and blocklisting that imposes zero overhead on open routes.
*   **Definition:**
    1. Create an `IpSet.java` configuration model in `nanogate-security` representing a reusable collection of IP addresses/CIDR blocks (fields: `name`, `type`, `ips`).
    2. Create an `IpSecurityService.java` that pre-compiles these string IPs into fast-lookup data structures (like Radix Trees) at application startup.
    3. Add an `ipSet` reference to the `Route` model in `nanogate-routing`.
    4. Modify `RoutingFilter.java` so that it validates the request IP against the configured `IpSet` only *after* a route is matched. If the route has no IP rules, the check is bypassed entirely.
*   **Use Case:** A developer defines an `IpSet` called "corporate-vpn". They can easily apply this restriction to 10 internal routes by just referencing the name "corporate-vpn", while 50 public routes remain unhindered by any IP validation logic.

## Task 2: Basic In-Memory Rate Limiting
*   **Goal:** Protect backend services from traffic spikes and abuse by limiting the number of requests allowed within a time window.
*   **Definition:**
    1. Introduce `resilience4j-ratelimiter` into the `nanogate-routing` or `nanogate-resilience` module.
    2. Add a `RateLimitProperties` model to the `Route` configuration to allow rate limits to be configured per route (e.g., `requestsPerSecond`).
    3. Create a `RateLimitFilter.java` that uses a `ConcurrentHashMap` to store individual `RateLimiter` instances keyed by the client's IP address.
    4. When a limit is exceeded, immediately return a `429 Too Many Requests` response.
*   **Use Case:** A public API route is limited to 10 requests per second per IP address to prevent a single client from overwhelming the backend database.

## Task 3: Centralized CORS Management
*   **Goal:** Allow NanoGate to cleanly handle Cross-Origin Resource Sharing (CORS) preflight requests and response headers without pushing this logic to backend services.
*   **Definition:**
    1. Add a `CorsConfig.java` to the `nanogate-routing` module.
    2. Define application properties (e.g., `nanogate.cors.allowed-origins`, `nanogate.cors.allowed-methods`, `nanogate.cors.max-age`).
    3. Register a Spring `CorsFilter` bean configured with a `UrlBasedCorsConfigurationSource` using the provided properties.
*   **Use Case:** A frontend Single Page Application (SPA) hosted on `domain-a.com` needs to make AJAX requests to the gateway at `api.domain-b.com`. The gateway handles the `OPTIONS` preflight request and adds the necessary `Access-Control-Allow-Origin` headers.

## Task 4: HTTP Response Caching
*   **Goal:** Reduce latency and backend load by caching responses for frequently requested, read-only data.
*   **Definition:**
    1. Create a `ResponseCacheFilter.java` in the `nanogate-caching` module.
    2. Add configuration to routes indicating if caching should be enabled, and the Time-To-Live (TTL).
    3. Intercept `GET` requests; if a valid cached response exists in the configured cache store (starting with an in-memory store like Caffeine, designed to switch to Redis later), return it immediately.
    4. If it's a cache miss, proxy the request, buffer the response, store it in the cache, and return it to the client.
*   **Use Case:** An endpoint serving a slow-changing product catalog is heavily hit. The gateway caches the response for 60 seconds, answering 99% of requests instantly without hitting the backend server.

## Task 5: Comprehensive Integration Testing
*   **Goal:** Prove that the cross-cutting filters function correctly within the routing lifecycle.
*   **Definition:**
    1. Write an `*IT.java` test in `nanogate-security` to verify IP blocklisting returns `403 Forbidden` and allows valid IPs.
    2. Write an `*IT.java` test in `nanogate-routing` to verify rate limit exhaustion triggers a `429` status code and enforces limits accurately over time.
    3. Write an `*IT.java` test in `nanogate-caching` to verify subsequent identical `GET` requests return cached data and do not increment a hit counter on the backend mock server.
