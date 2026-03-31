# NanoGate Project Implementation Phases

This document outlines the phased approach for developing the **NanoGate** API Gateway for Azure. Each phase is designed to deliver a stable, testable set of features, gradually building up from a core routing engine to a fully observable, production-ready cloud-native gateway.

---

## Phase 1: Foundation & Core Routing (MVP)
**Goal:** Establish a professional baseline architecture featuring a decoupled "Backend Set" configuration model, a hierarchical HTTP client configuration, and implement the core synchronous routing engine with a pluggable, round-robin load balancer.

*   **Project Setup:** Initialize the multi-module Spring Boot project with a virtual-thread-based web server.
*   **Backend Set Architecture:** Implement a configuration model that separates backend server pools (`Backend Sets`) from routing rules (`Routes`).
*   **Hierarchical HTTP Client Configuration:** Implement a three-tiered (Global -> Backend Set -> Route) configuration model for backend HTTP client properties (e.g., timeouts).
*   **Synchronous Routing Engine:** Implement a core routing filter that maps incoming requests to a `Route` and then to its designated `Backend Set`.
*   **Pluggable Load Balancing:** Implement a `LoadBalancerFactory` and the initial **Round-Robin** algorithm. Routes will be able to override the default load balancer defined in the `Backend Set`.
*   **Containerization:** Create initial Dockerfiles for local testing.

---

## Phase 2: Resiliency & Advanced Routing
**Goal:** Ensure the gateway can handle backend failures gracefully and support complex, modern application protocols.

*   **Asynchronous, WebSocket & SSE Support:** Upgrade the routing engine to handle non-blocking asynchronous requests, seamlessly proxy WebSocket (ws/wss) connections, and support Server-Sent Events (SSE) for streaming capabilities.
*   **Resiliency Engine:**
    *   Implement **Circuit Breakers** to prevent cascading failures.
    *   Configure **Timeouts** for backend calls.
    *   Enable **Automatic Retries** for safe, idempotent requests.
*   **Active Health Checking:** Implement a background worker to continuously ping downstream `/health` endpoints and automatically remove unhealthy instances from the load balancer pool.
*   **API Versioning:** Add native support for routing based on URL paths (`/v1/`) and HTTP headers (`Accept-Version`).

---

## Phase 3: Cross-Cutting Concerns & Transformation
**Goal:** Introduce the necessary API management features that decouple clients from backend complexities.

*   **Scripting / Request Transformer:** Integrate a lightweight scripting engine (e.g., GraalVM JavaScript or a custom DSL) to allow dynamic modification of URLs, headers, and JSON payloads on the fly.
*   **Rate Limiting:** Implement basic in-memory rate limiting, with an architecture designed to plug in Redis for distributed limits later.
*   **CORS & Caching:** Add centralized management for Cross-Origin Resource Sharing (CORS) policies and basic HTTP response caching.
*   **Lightweight Threat Protection:** Implement payload size restrictions to prevent OOM errors and basic IP allowlisting/blocklisting capabilities.

---

## Phase 4: Dynamic Configuration & Security
**Goal:** Enable zero-downtime management and secure the API traffic.

*   **Zero-Downtime Configuration Management:** 
    *   Move routing rules out of local properties.
    *   Integrate with external stores (Azure App Configuration or Blob Storage).
    *   Implement polling and webhook listeners to atomically hot-reload routing tables in memory without restarting instances.
*   **Authentication/Authorization:** Integrate standard OAuth2/OIDC flows to validate JWT tokens using an external Identity Provider (e.g., Entra ID) before forwarding requests.
*   **Advanced Service Discovery (Optional/Pluggable):** Add support for querying dedicated service registries (like Consul or Azure Service Registry) for hybrid environments.
*   **Distributed Rate Limiting:** Implement the Redis-backed distributed rate limiter for cluster-wide enforcement.

---

## Phase 5: Observability & Production Readiness
**Goal:** Provide "single pane of glass" visibility and finalize the system for production Azure deployment.

*   **Deep Observability:**
    *   **Metrics:** Expose detailed instance-level metrics (throughput, latency, memory) via OpenTelemetry.
    *   **Tracing:** Implement distributed tracing headers to correlate requests across the gateway and downstream microservices.
    *   **Logging:** Ensure all access and error logs are structured (JSON) for easy aggregation in Azure Monitor.
*   **Infrastructure as Code (IaC):** Finalize Helm charts and ARM/Bicep templates for seamless deployment to Azure Kubernetes Service (AKS) or Azure Virtual Machine Scale Sets (VMSS).
*   **Performance Benchmarking:** Conduct rigorous load testing to prove the "low overhead" and horizontal scaling requirements under massive traffic spikes.
*   **Final Documentation:** Complete operational runbooks, architecture diagrams, and developer guides.

---

## Phase 6: Azure Marketplace Publication
**Goal:** Package **NanoGate** as a professional, easy-to-deploy solution for the Azure Marketplace.

*   **Azure Managed Application Package:** This is the core technical work. Instead of just providing internal-use Helm charts, this involves creating:
    *   `createUiDefinition.json`: A file that defines a user-friendly UI wizard for the Azure Portal.
    *   `mainTemplate.json`: A master ARM template that deploys the entire solution (networking, gateway cluster, optional Redis, Log Analytics workspace, etc.) based on the user's UI input.
*   **Container Image Publication:** Publish final, versioned, and security-scanned Docker images to a public or accessible container registry (e.g., Azure Container Registry).
*   **Marketing & Legal Assets:**
    *   Create compelling marketing descriptions, logos, and icons that meet Azure Marketplace specifications.
    *   Prepare official Terms of Use and Privacy Policy documents.
*   **Submission & Certification:** Submit the complete offer through the Microsoft Partner Center portal. This will trigger a multi-stage validation and certification process by Microsoft, which may require several rounds of feedback and fixes.
*   **Post-Launch Support Plan:** Define and document a clear process for handling customer support inquiries, bug fixes, and version updates for the marketplace offer.

---

## Phase 7: Advanced Configuration & Customization
**Goal:** Enhance NanoGate's flexibility by exposing advanced configuration options for core components, allowing for fine-tuned performance and behavior.

*   **Extended HTTP Client Configuration:**
    *   Add support for configuring HTTP protocol versions (HTTP/1.1, HTTP/2) on a hierarchical basis (Global -> Backend Set -> Route).
    *   Implement configurable redirect policies (NEVER, ALWAYS, NORMAL) for outgoing backend requests, also following the hierarchical override model.
    *   Introduce options for configuring outgoing proxy settings for backend communication.
*   **Advanced Load Balancing Strategies:**
    *   Implement the **Least Connections** load balancing algorithm, considering both instance-local and distributed state management.
    *   Explore and implement other advanced algorithms like Weighted Round Robin or IP Hash.
*   **Customizable Health Checks:** Allow configuration of health check paths, intervals, and failure thresholds per Backend Set.
*   **Fine-Grained Resiliency Tuning:** Expose more detailed configuration for Circuit Breaker thresholds, retry policies, and timeout durations.