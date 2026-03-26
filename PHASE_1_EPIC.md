# Epic: NanoGate Phase 1 - Foundation & Core Routing (MVP)

**Epic Goal:** Establish the foundational architecture for the NanoGate API Gateway and implement the core, lightweight synchronous routing engine capable of discovering and load-balancing traffic to downstream microservices.

**Description:** This epic covers the initial setup of the Spring Boot application and the development of the minimal viable product (MVP). At the end of this epic, NanoGate will be able to receive incoming HTTP requests, determine the correct backend service based on configuration, forward the request using a basic load-balancing strategy, and return the response to the client. The application will also be containerized for easy testing.

---

## Task 1: Project Initialization & Core Setup

*   **Goal:** Create the skeletal structure of the NanoGate application with the necessary foundational dependencies.
*   **Definition:** Initialize a Spring Boot 3.x/4.x project (or equivalent chosen stack) configured for web routing. This includes setting up the basic project structure, build tool (Maven/Gradle), and adding essential libraries (e.g., Spring Cloud Gateway core or equivalent lightweight proxy libraries if building from scratch).
*   **Use Case:** A developer needs to clone the repository and build the project locally without errors to start contributing.
*   **Inputs:**
    *   Project requirements (Java version, Spring Boot version).
    *   List of core dependencies (Webflux/WebMVC, Proxy libs).
*   **Outputs:**
    *   A compilable project repository (e.g., `pom.xml` or `build.gradle`).
    *   A runnable main application class.
    *   Basic application configuration file (`application.yml` or `application.properties`).

## Task 2: Core Synchronous Routing Engine

*   **Goal:** Enable NanoGate to forward incoming HTTP requests to a specified destination based on static rules.
*   **Definition:** Implement the core routing logic. The gateway must inspect incoming request URLs (paths) and HTTP headers to match them against a set of configured routing rules. Upon a match, it must construct a new HTTP request to the target URL, forward it synchronously, and proxy the response back to the client.
*   **Use Case:** An external client sends a request to `https://nanogate.example.com/api/users/123`. The gateway matches the `/api/users/**` path and forwards the request to the internal User Service at `http://internal-user-service/123`.
*   **Inputs:**
    *   Incoming HTTP Request (Method, URL, Headers, Body).
    *   Static routing configuration (defined in `routes.yml` for this phase which is addressed by a property in `application.yml` file).
*   **Outputs:**
    *   Proxied HTTP Response from the backend service.
    *   If no route matches, a `404 Not Found` response.

## Task 3: Basic Load Balancing Implementation

*   **Goal:** Distribute incoming traffic across multiple instances of a downstream service.
*   **Definition:** Enhance the routing engine to support multiple target URLs for a single route. Implement in-memory load balancing algorithms: specifically, **Round-Robin** (distributing requests sequentially) and **Least-Connections** (sending the request to the backend with the fewest active requests).
*   **Use Case:** The User Service has three healthy instances. NanoGate receives three sequential requests for the User Service and routes the first request to Instance A, the second to Instance B, and the third to Instance C.
*   **Inputs:**
    *   A matched routing rule containing a list of available backend target URLs.
    *   The chosen load balancing strategy configuration.
*   **Outputs:**
    *   A single selected target URL for the current request.

## Task 4: DNS-Based Service Discovery Integration

*   **Goal:** Allow NanoGate to resolve backend services using internal DNS names rather than hardcoded IP addresses.
*   **Definition:** Configure the gateway to resolve target URLs utilizing the underlying environment's DNS resolution (e.g., Kubernetes CoreDNS). The gateway should be able to take a target like `http://user-service` and successfully route to it, relying on the OS/JVM DNS resolver to find the actual IP address.
*   **Use Case:** NanoGate is deployed in Kubernetes. A route is configured to forward to `http://order-service`. The gateway seamlessly resolves this internal Kubernetes service name to the correct cluster IP without needing a separate service registry like Eureka.
*   **Inputs:**
    *   Target URL containing a DNS hostname instead of an IP address.
*   **Outputs:**
    *   Successfully established connection to the resolved backend service.

## Task 5: Containerization & Local Deployment

*   **Goal:** Package NanoGate into a standardized Docker image for consistent deployment across environments.
*   **Definition:** Write a `Dockerfile` to build a lightweight container image for the NanoGate application. Optimizations should be considered (e.g., multi-stage builds, minimal base images like Alpine or Distroless) to align with the "lightweight" goal. Provide a `docker-compose.yml` file to easily spin up NanoGate alongside a dummy backend service for local testing.
*   **Use Case:** A QA engineer wants to test the routing rules. They run `docker-compose up`, which starts NanoGate and a mock backend, allowing them to send requests immediately without setting up a Java environment.
*   **Inputs:**
    *   Compiled application artifact (e.g., `.jar` file or native executable).
*   **Outputs:**
    *   A functional `Dockerfile`.
    *   A `docker-compose.yml` demonstrating a basic working setup.
    *   A built container image available locally.