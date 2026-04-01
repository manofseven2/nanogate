# Epic: NanoGate Phase 1 - Foundation & Core Routing (MVP)

**Epic Goal:** Establish a professional baseline architecture featuring a decoupled "Backend Set" configuration model, a hierarchical HTTP client configuration, and implement the core synchronous routing engine with a pluggable, round-robin load balancer.

**Description:** This epic focuses on building a robust and maintainable foundation for NanoGate. The core architectural change is to separate the concept of backend server pools (`Backend Sets`) from the routing rules (`Routes`). This allows for cleaner configuration and centralized management of backend policies.

---

## Task 1: Project Structure & Initial Configuration Models

*   **Goal:** Implement a new configuration structure that separates backend server definitions from routing rules.
*   **Definition:**
    1.  Create a `BackendSet.java` model class containing `name`, `loadBalancer` (default), and `servers` (list of URIs).
    2.  Refactor the `Route.java` model to remove `targetUris` and `loadBalancer`, replacing them with a `String backendSet` reference and an optional `String loadBalancer` for overrides.
    3.  Update `NanoGateRouteProperties.java` to include a `List<BackendSet> backendSets`.
*   **Use Case:** A developer can now define a pool of servers once in a `backend-set` and refer to it by name in multiple `routes`, reducing configuration duplication.
*   **Inputs:** The new YAML structure with `backend-sets` and `routes`.
*   **Outputs:** Java model classes that accurately reflect the new configuration hierarchy.

## Task 2: Implement Hierarchical HTTP Client Configuration

*   **Goal:** Implement a three-tiered, configurable, and cached provider for backend `HttpClient` instances.
*   **Definition:**
    1.  Create an `HttpClientProperties.java` model to hold client settings (e.g., `connectTimeout`).
    2.  Add an optional `HttpClientProperties` field to the `Route`, `BackendSet`, and global `NanoGateRouteProperties` models.
    3.  Create a new `HttpClientProvider.java` service. This service will be responsible for creating and caching `HttpClient` instances based on a final, merged set of properties.
    4.  Refactor `RoutingFilter.java` to calculate the final merged `HttpClientProperties` for each request by applying the Route -> Backend Set -> Global override logic.
    5.  Refactor `RequestProxy.java` to use the `HttpClientProvider` to get a client for each request instead of creating its own.
*   **Use Case:** A global connect timeout is set to 5s. A specific route for a slow service overrides this to 30s. The `RoutingFilter` calculates the final 30s timeout and the `RequestProxy` uses a client with that specific setting.
*   **Inputs:** A request's `Route` and `BackendSet`.
*   **Outputs:** A correctly configured and potentially cached `HttpClient` instance for proxying.

## Task 3: Implement Intelligent Core Routing

*   **Goal:** Implement the core routing logic, ensuring that more specific routes are always prioritized.
*   **Definition:**
    1.  Refactor `InMemoryRouteLocator.java` to sort routes by path specificity (e.g., using `AntPathMatcher.getPatternComparator()`) before attempting to find a match. This ensures that a request to `/api/users/reports` will match `/api/users/reports/**` before it matches `/api/users/**`.
    2.  Modify the `LoadBalancer.java` interface to accept a `BackendSet` object.
    3.  Update `RoundRobinLoadBalancer.java` to work with the new interface signature.
    4.  Refactor `RoutingFilter.java` to implement the core orchestration: find the most specific route, look up its `BackendSet`, determine the correct load balancer to use (respecting overrides), and proxy the request.
*   **Use Case:** A request to `/api/users/reports/1` correctly matches the `/api/users/reports/**` route, even if the more general `/api/users/**` route is defined first in the configuration file.
*   **Inputs:** An incoming `HttpServletRequest`.
*   **Outputs:** The single, most specific `Route` that matches the request.

## Task 4: Write Unit and Mock Tests

*   **Goal:** Ensure code quality and prevent regressions by writing comprehensive unit and mock tests for all core classes developed in the project.
*   **Definition:**
    1.  Create tests for all classes in the `config` package (e.g., `NanoGateRouteProperties`, `RoutingProperties`, `RouteConfiguration`), verifying configuration loading, mapping, and validation logic.
    2.  Create mock tests for all classes in the `filter` package (e.g., `RoutingFilter`), verifying the core orchestration logic, including finding routes, resolving backend sets, calculating HTTP client properties, load balancing, and proxying. Mock necessary dependencies.
    3.  Create unit tests for all classes in the `model` package (e.g., `Route`, `BackendSet`, `LoadBalancerType`, `HttpClientProperties`) to verify their logic, data integrity, and potential utility methods.
    4.  Create tests for all classes in the `service` package (e.g., `LoadBalancer`, `RequestProxy`, `RouteLocator`, `HttpClientProvider`, `LoadBalancerFactory`, `InMemoryRouteLocator`, `RoundRobinLoadBalancer`). Mock external dependencies where necessary and verify specific logic like round-robin algorithms, route sorting, and client generation.
*   **Use Case:** A developer refactors a piece of code and runs the test suite to confidently verify that no existing functionality across any package was broken.
*   **Inputs:** The implementation classes in the `config`, `filter`, `model`, and `service` packages.
*   **Outputs:** A suite of passing unit tests using standard testing frameworks (e.g., JUnit 5, Mockito) that cover all classes in the specified packages.

## Task 5: Write Integration Tests (ITs)

*   **Goal:** Verify the interactions between the routing components within a realistic Spring Boot context by writing integration tests.
*   **Definition:**
    1.  Configure the `maven-failsafe-plugin` in the `pom.xml` files to execute tests ending with `*IT.java` and enable parallel test execution to speed up the pipeline.
    2.  Write integration tests covering realistic scenarios for `RoutingFilter`, `InMemoryRouteLocator`, `LoadBalancerFactory`, `RequestProxy`, and `RoundRobinLoadBalancer`.
    3.  Tests should spin up a full or sliced Spring context to ensure that beans are correctly wired, properties are injected from YAML correctly, and the core routing chain works from end to end without excessive mocking.
    4.  Ensure test file names follow the `*IT` suffix convention (e.g., `RoutingFilterIT.java`).
*   **Use Case:** The application is built, and the CI/CD pipeline runs the `verify` phase. The Failsafe plugin executes the ITs concurrently, ensuring that not only do the individual components work, but they also wire together correctly as a fully functioning Spring Boot application.
*   **Inputs:** The completed Spring Boot application context and configuration.
*   **Outputs:** Failsafe plugin configured in `pom.xml` and passing `*IT.java` classes.

## Task 6: Containerization & Local Deployment

*   **Goal:** Package NanoGate into a standardized Docker image for consistent deployment across environments.
*   **Definition:** Write a `Dockerfile` to build a lightweight container image for the NanoGate application. Provide a `docker-compose.yml` file to easily spin up NanoGate alongside a dummy backend service for local testing.
*   **Use Case:** A QA engineer can run `docker-compose up` to test the new Backend Set routing logic without needing a local Java environment.
*   **Inputs:** Compiled application artifact (`.jar` file).
*   **Outputs:** A functional `Dockerfile` and `docker-compose.yml`.

## Task 7: Future - Least Connections Load Balancer

*   **Goal:** Implement a more dynamic load balancing algorithm that adapts to server load.
*   **Definition:** Create a new `LeastConnectionsLoadBalancer` that implements the `LoadBalancer` interface. This implementation will need to track the number of active connections to each backend URI within a `BackendSet`. The design should consider both a simple in-memory implementation for local state and a future-proof hook for using a distributed cache (like Redis) for global state.
*   **Use Case:** A backend service has two instances. Instance A is busy processing a slow request. The gateway receives a new request and, seeing that Instance B has zero active connections, forwards the request to Instance B.
*   **Inputs:** A mechanism to track active connection counts per URI (e.g., an in-memory map of AtomicIntegers).
*   **Outputs:** The backend URI with the lowest number of active connections.