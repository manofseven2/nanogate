# NanoGate Pluggable Service Discovery

## 1. Overview
The **Service Discovery** component within NanoGate is a robust, dynamic mechanism that allows the API gateway to locate the physical IP addresses and ports of downstream backend services in real-time. By moving away from static, hard-coded IP lists, NanoGate can seamlessly operate in highly volatile environments like Kubernetes clusters or cloud-native networks where infrastructure is ephemeral.

NanoGate's Service Discovery architecture is built around three core principles:
1. **Pluggability (Strategy Pattern)**: Support multiple discovery mechanisms out-of-the-box while allowing engineers to easily add proprietary ones.
2. **High Performance**: Ensure sub-millisecond route resolution via caching to prevent overwhelming external registries.
3. **Consistency**: Maintain absolute synchronization with the Zero-Downtime Configuration Reloading engine.

---

## 2. Supported Discovery Strategies

The system delegates resolution to the correct mechanism via the `DiscoveryType` enumerated on the `BackendSet` configuration.

### A. STATIC (Default)
The traditional approach. Used when `DiscoveryType` is omitted or explicitly set to `STATIC`.
*   **Algorithm**: Simply returns the static list of `servers` explicitly defined in the `application.yml` or dynamic routes file.
*   **Use Case**: Legacy infrastructure, external 3rd-party APIs, or hardware load balancers that expose a single static IP/hostname.

### B. DNS (Kubernetes Headless Services)
Allows NanoGate to perform client-side load balancing directly against Kubernetes pods rather than routing traffic through the standard Kube-proxy `Service` IP.
*   **Algorithm**: NanoGate executes a native DNS `A` (and optionally `SRV`) record lookup against the provided hostname. If a Kubernetes Headless Service (a service with `clusterIP: None`) is queried, the K8s DNS server returns multiple `A` records—one for every healthy Pod backing the service.
*   **Use Case**: Bypassing Kubernetes internal load balancers to achieve smarter, gateway-level traffic distribution (e.g., Least Connections, Weighted Round Robin) directly to Pods.

### C. CONSUL
Native integration with HashiCorp Consul. 
*   **Algorithm**: NanoGate queries the Consul HTTP API catalog (`/v1/catalog/service/{serviceId}`) to retrieve all healthy node IP addresses associated with a specific logical service name.
*   **Architectural Note**: NanoGate utilizes a *native* Java 11 `HttpClient` implementation rather than `spring-cloud-commons`. This architectural pivot prevents heavy classpath contamination, sidesteps Spring Boot 4.x compatibility issues, and aligns with NanoGate's mission to remain incredibly lightweight.
*   **Use Case**: Multi-cloud or VM-based environments utilizing Consul as a central service mesh registry.

---

## 3. Core Business Logic & Resolution Algorithm

When a request arrives, NanoGate must identify where to route it. The process is orchestrated across multiple layers:

### Step 1: Route Resolution
The incoming request matches a `Route` (e.g., `/api/users/**`), which points to a specific logical `BackendSet`.

### Step 2: Cache Interception (`ServiceDiscoveryRegistry`)
The `RoundRobinLoadBalancer` asks the `ServiceDiscoveryRegistry` to resolve the `BackendSet`. 
1. The Registry generates a deterministic cache key: `${backendSetName}:${discoveryType}:${serviceId}`.
2. It queries an internal **Caffeine Cache**.
3. If the data exists and is younger than 5 seconds (configurable TTL), the cached list of server URIs is returned instantly (Sub-millisecond resolution).

### Step 3: Background Refresh (`ServiceDiscoveryRefresher`)
To eliminate the discovery latency spike when a cache entry expires during a live request, NanoGate includes a background refresher:
1. A scheduled task runs every 5 seconds (configurable via `nanogate.routing.discovery.refresh-interval`).
2. It iterates through all active `BackendSets` in the `RouteRegistry`.
3. For dynamic types (DNS, CONSUL), it pre-emptively calls discovery and populates the cache.
4. This ensures that the cache is almost always "warm" before a request actually needs the IPs.

### Step 4: Provider Delegation (Strategy Pattern)
If a request arrives and the cache is somehow empty (e.g., immediately after a configuration reload), the Registry iterates through a list of injected `ServiceDiscoveryProvider` components.
It calls `provider.supports(backendSet.getDiscoveryType())` until it finds the matching strategy.

### Step 5: Live Resolution & Fallback
The selected provider executes its network call (e.g., HTTP request to Consul, DNS lookup).
*   **Success**: The provider parses the response, maps it into a `List<URI>`, and returns it to the Registry, which stores it in the cache.
*   **Failure**: If the provider fails (e.g., Consul is down, DNS timeout), it catches the exception, logs an error, and gracefully returns an empty list (`[]`).

### Step 6: Active Health Verification Filter
The raw list of discovered URIs is **not** blindly trusted. Before traffic is routed, the Load Balancer filters the discovered URIs through the `ActiveHealthCheckService`.
Only servers that have recently passed their explicit `/health` endpoint checks are kept in the pool.
If no servers are left, the gateway returns a `503 Service Unavailable`.

---

## 4. Zero-Downtime Configuration Synchronization

A major technical challenge with cached service discovery is avoiding "stale states" when an administrator hot-swaps the gateway's configuration.

For example, if an administrator edits the `external-routes.yml` file to switch a backend set from `STATIC` to `CONSUL`, a standard caching mechanism would continue using the cached static IPs until the TTL expires, leading to split-brain routing.

NanoGate completely eliminates this via **Event Hook Integration**:
1. The `YamlFilePollingProvider` detects a filesystem modification on the configuration file.
2. It parses the new configuration and passes it to the `RouteRegistry`.
3. Before the `RouteRegistry` atomically swaps the new routes into memory, it triggers a direct call to `serviceDiscoveryRegistry.invalidateCache()`.
4. The cache is entirely flushed.
5. The very next inbound HTTP request is forced to resolve the new backend configuration instantly.

Furthermore, the structure of the Cache Key (`name:type:serviceId`) guarantees that dynamically changing a backend's discovery type instantly invalidates older cached entries, ensuring 100% routing consistency.
