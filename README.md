# NanoGate API Gateway

NanoGate is a lightweight, scalable, and cloud-native API Gateway built with Spring Boot. It is designed to be highly performant, with a minimal footprint, making it ideal for modern microservices architectures deployed on platforms like Azure. The project is optimized for native compilation using Spring AOT, ensuring fast startup times and low memory consumption.

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