# Epic: NanoGate Phase 4 - Dynamic Configuration & Security

**Epic Goal:** Enable zero-downtime management, distributed resilience, and secure the API traffic to ensure NanoGate is ready for cloud-native, clustered environments.

---

## Task 1: Zero-Downtime Route Configuration
*   **Goal:** Allow NanoGate to update its routing rules and backend definitions on the fly without restarting the application or dropping active connections, while still fully supporting Spring Boot native environment variables and profiles for initial startup.
*   **Definition:**
    1. Define a generic `ConfigurationProvider` interface in `nanogate-routing` for fetching configurations.
    2. Implement a `SpringNativeConfigurationProvider` to seed the initial routes from standard `application.yml` and environment variables.
    3. Implement a `YamlFilePollingProvider` that watches an external `routes.yml` file and hot-reloads when the file is modified (e.g., via Kubernetes ConfigMaps).
    4. Introduce an `AtomicReference` or `ReadWriteLock` backed route table (`RouteRegistry`) to safely swap the routing configuration in memory.
    5. Implement a `ConfigurationRefreshService` that periodically polls the active providers to reload the configuration.
*   **Use Case:** An administrator modifies the `routes.yml` file in an external mounted directory. Within seconds, all NanoGate instances detect the change, parse the new YAML, hot-reload their internal route tables, and begin routing traffic to the new endpoints with zero downtime.

## Task 2: JWT Authentication & Authorization (OAuth2/OIDC)
*   **Goal:** Secure downstream services by validating JWT access tokens at the gateway level.
*   **Definition:**
    1. Add the `spring-boot-starter-oauth2-resource-server` dependency to `nanogate-security`.
    2. Implement a `JwtAuthenticationFilter` that intercepts requests on secured routes, parses the `Authorization: Bearer <token>` header, and validates the JWT signature against a configured JWKS URI (e.g., Azure Entra ID or Auth0).
    3. Update the `Route` model to accept an `authentication` property indicating if a route is public or requires validation.
    4. Provide the ability to forward specific JWT claims as HTTP headers to the downstream backend (e.g., `X-User-Id`, `X-User-Roles`).
*   **Use Case:** A client requests an internal API. The gateway intercepts the request, validates the JWT, strips the token, and forwards only the extracted user ID and roles as HTTP headers to the backend service.

## Task 3: Distributed Rate Limiting (Redis Integration)
*   **Goal:** Transition the existing in-memory rate limiter to a distributed, Redis-backed rate limiter to enforce quotas accurately across a cluster of NanoGate instances.
*   **Definition:**
    1. Add `spring-boot-starter-data-redis` to `nanogate-resilience`.
    2. Implement a new `RedisRateLimiterService` implementing the existing `RateLimiterService` interface.
    3. Write a Lua script implementing the **Token Bucket** algorithm to perform atomic `check-and-decrement` operations for distributed rate limiting buckets (chosen for high accuracy, low memory usage, and burst support).
    4. Add configuration toggles to switch between `IN_MEMORY` and `REDIS` rate limiter implementations based on the environment profile.
*   **Use Case:** A public API is restricted to 100 requests per minute per IP. With 5 NanoGate instances running in Kubernetes, a user makes 100 requests spread across all instances. The Redis backend tracks the global count, successfully blocking the 101st request regardless of which node receives it.

## Task 4: Pluggable Service Discovery Integration
*   **Goal:** Allow NanoGate to dynamically discover backend service IP addresses and ports using various external registries and mechanisms, ensuring compatibility with all Kubernetes scenarios and famous registries (Consul, Eureka).
*   **Definition:**
    1. Create a `ServiceDiscoveryProvider` strategy interface in `nanogate-routing` to abstract different resolution mechanisms.
    2. Implement a `SpringCloudDiscoveryProvider` that leverages Spring Cloud's `DiscoveryClient` to transparently support Consul, Eureka, Zookeeper, and the Kubernetes API via drop-in starter dependencies.
    3. Implement a `DnsServiceDiscoveryProvider` to natively query A/SRV records, guaranteeing support for Kubernetes Headless Services and generic DNS-based client-side load balancing.
    4. Integrate the active `ServiceDiscoveryProvider` into the `RoundRobinLoadBalancer` with a caching layer (e.g., Caffeine) to prevent registry/DNS polling on every request.
*   **Use Case:** NanoGate is deployed in a hybrid environment. It discovers a legacy `payment-service` via Consul, a newer `auth-service` via the Kubernetes API, and a `cache-cluster` via Kubernetes Headless Service DNS. NanoGate handles all routing and client-side load balancing uniformly.

## Task 5: Phase 4 Integration & Chaos Testing
*   **Goal:** Validate that configuration hot-reloading, security validations, and Redis connections function securely under load and failure conditions.
*   **Definition:**
    1. Write an IT in `nanogate-routing` using Testcontainers to mock Azure App Config / External Polling and verify routing logic seamlessly transitions between old and new configs.
    2. Write an IT in `nanogate-security` using WireMock to host a mock JWKS endpoint, proving invalid tokens are rejected (`401 Unauthorized`) and valid tokens are accepted.
    3. Write an IT in `nanogate-resilience` using Testcontainers for Redis to prove rate limits are correctly shared across parallel execution threads.
