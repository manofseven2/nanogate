# NanoGate - A Lightweight, Cloud-Native API Gateway

**NanoGate** is a lightweight, performant, and highly scalable API Gateway designed for modern cloud-native architectures. Built with Java 21+ and Spring Boot 4, it serves as a powerful reverse proxy to manage, secure, and observe API traffic for downstream microservices.

The primary goal of NanoGate is to provide a streamlined and efficient alternative to complex, enterprise-grade API gateways, focusing on low overhead, elastic scalability, and deep observability. It is designed from the ground up to be deployed on cloud platforms like Microsoft Azure and optimized for modern deployment practices, including containerization and native compilation with GraalVM.

## Core Features

- **Dynamic & Intelligent Routing:** Route requests based on URL paths, headers, and HTTP methods. The routing engine prioritizes more specific path patterns, ensuring predictable and accurate traffic management.
- **Pluggable Load Balancing:** Distribute traffic across downstream services with built-in strategies like Round-Robin and Least Connections. The factory pattern allows for easy extension with custom algorithms.
- **Resiliency & Fault Tolerance:** Prevent cascading failures with integrated Circuit Breakers, Timeouts, and Automatic Retries for idempotent requests.
- **Transformation & Manipulation:** Natively support adding, removing, or editing HTTP headers. The architecture also allows for advanced request/response transformation via scripting.
- **Protocol Support:** Seamlessly proxy standard HTTP, asynchronous requests, and modern protocols like WebSockets and Server-Sent Events (SSE).
- **Deep Observability:** Gain granular visibility into the health, performance, and traffic of every gateway instance with integrated metrics, logging, and distributed tracing support (e.g., OpenTelemetry).
- **Zero-Downtime Configuration:** Dynamically update routing rules on-the-fly by connecting to external configuration stores (like Azure App Configuration or a Redis cache) without requiring service restarts.
- **Security:** Secure your APIs with features like JWT validation (OAuth2/OIDC), rate limiting, and IP allowlisting/blocklisting.

## Technology Stack

- **Java 21+:** Leverages the latest features of the Java platform.
- **Spring Boot 4.x:** Provides a robust, auto-configured foundation.
- **Virtual Threads:** Uses Java 21's virtual threads for highly concurrent, non-blocking I/O, maximizing throughput with a simple, synchronous coding model.
- **Maven:** For dependency management and build automation.
- **Docker:** For containerization and consistent deployments.
- **JUnit 5 & Mockito:** For comprehensive unit, mock, and integration testing.

## Project Structure

This project follows a multi-module Maven structure to ensure a clean separation of concerns, making the gateway extensible and maintainable.

### Modules

Below is a description of each module and its responsibilities within the NanoGate ecosystem.

#### `nanogate-parent`
This is the master POM for the entire project. It uses `pom` packaging and manages the versions of all dependencies for every submodule via the `<dependencyManagement>` section. This ensures consistency and avoids version conflicts across the project.

#### `nanogate-app`
The main application module that brings everything together. It contains the main Spring Boot application class (`NanoGateApplication`) and is responsible for assembling the final executable JAR. This module's `pom.xml` includes all other `nanogate-*` modules as dependencies and is configured with the `spring-boot-maven-plugin` and the `native-maven-plugin` to enable Spring AOT processing and native image generation.

#### `nanogate-routing`
This module contains the heart of the gateway: the core routing engine. It is built upon Spring Cloud Gateway and is responsible for:
*   Dynamic routing based on paths, headers, and methods.
*   Load balancing across downstream services (Round-Robin, etc.).
*   Service discovery integration (e.g., Consul, Kubernetes DNS).

#### `nanogate-resilience`
Handles fault tolerance and makes the gateway resilient to backend failures. It integrates with Resilience4j to provide:
*   **Circuit Breakers:** To stop cascading failures.
*   **Retries:** To automatically retry failed idempotent requests.
*   **Timeouts:** To prevent long-running requests from consuming resources.

#### `nanogate-scripting`
Provides capabilities for dynamic request and response transformation. While originally planned to use GraalVM directly, the focus is now on leveraging Spring Cloud Gateway's built-in filtering capabilities, which are AOT-compatible. This module is the designated place for any custom filter factories or complex transformation logic.

#### `nanogate-security`
This module is responsible for securing the APIs exposed by the gateway. Its responsibilities include:
*   **Authentication:** Validating JWTs via OAuth2/OIDC resource server support.
*   **Authorization:** Enforcing access control rules.
*   **Threat Protection:** Implementing basic security measures like IP allowlisting and payload size limits.

#### `nanogate-protocol-support`
Adds support for protocols beyond standard HTTP/1.1. This module enables NanoGate to:
*   Proxy **WebSocket** connections (`ws://` and `wss://`).
*   Act as a reverse proxy for **gRPC** services.

#### `nanogate-caching`
Implements response caching to reduce latency and backend load. It uses Spring's Cache abstraction with a Redis backend for distributed caching across multiple gateway instances.

#### `nanogate-observability`
Provides deep visibility into the gateway's behavior and performance. It integrates with:
*   **Spring Boot Actuator:** For health checks and basic metrics.
*   **Micrometer and OpenTelemetry:** For exposing detailed metrics and distributed traces, allowing integration with monitoring systems like Prometheus and Jaeger.