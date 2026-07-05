# Epic: NanoGate Phase 5 - Observability & Production Readiness

**Epic Goal:** Provide "single pane of glass" visibility and finalize the system for production Azure deployment.

*(Note: Basic OpenTelemetry metrics and distributed tracing were previously implemented in Phase 2. The tasks below represent the remaining, unimplemented requirements for this phase.)*

---

## Task 1: Structured Access & Error Logging
*   **Goal:** Ensure all access and error logs are structured (JSON) for easy aggregation in Azure Monitor.
*   **Definition:**
    1. Configure Logback to use a JSON encoder (e.g., `logstash-logback-encoder`).
    2. Implement a global Access Logging filter in `nanogate-routing` that logs request URI, method, duration, status code, client IP, and Trace ID.
    3. Implement structured error logging for exceptions (CircuitBreakerOpenException, RateLimitExceededException).
    4. Add a `logback-spring.xml` configuration to support dynamic log level adjustments at runtime without restarts.
*   **Use Case:** A production issue occurs in the API gateway. The operations team can easily query Azure Log Analytics for all 500-level errors or filter by a specific Trace ID, because every log entry is reliably structured in a queryable JSON format.


## Task 2: CI/CD Pipelines & Containerization
*   **Goal:** Automate the build, test, and containerization processes using GitHub Actions to continuously deliver production-ready artifacts.
*   **Definition:**
    1. Create a GitHub Actions workflow (`.github/workflows/ci.yml`) that triggers on pull requests to run the Maven build, unit tests, and integration tests.
    2. Create a Dockerfile optimized for Spring Boot / Java 25, leveraging layered JARs and a minimal base image (e.g., Eclipse Temurin JRE or Alpine).
    3. Create a GitHub Actions workflow (`.github/workflows/publish.yml`) that builds the Docker image and pushes it to a container registry (e.g., GitHub Packages, Azure Container Registry) upon merging to `main`.
    4. Implement semantic versioning (SemVer) and automated Git tagging for releases.
*   **Use Case:** A developer submits a pull request. The CI pipeline automatically spins up, runs all Unit and Integration tests, and reports success. Once merged, the CD pipeline builds a hardened Docker image and pushes it to the registry, ready for deployment to AKS or VMSS.

## Task 3: Infrastructure as Code (IaC) for Kubernetes
*   **Goal:** Finalize Helm charts for seamless deployment to Azure Kubernetes Service (AKS).
*   **Definition:**
    1. Create a `helm/nanogate` directory structure.
    2. Create Kubernetes `Deployment`, `Service`, and `ConfigMap` templates.
    3. Configure Horizontal Pod Autoscaler (HPA) templates based on CPU and memory utilization.
    4. Support injecting secrets (e.g., keystores, external URL tokens) via the Azure Key Vault Secrets Provider class.
    5. Test the Helm deployment locally using minikube or docker-desktop kubernetes.
*   **Use Case:** A DevOps engineer uses `helm install nanogate ./helm/nanogate` in a CI/CD pipeline. The chart instantly spins up the gateway pods, configures auto-scaling policies, and mounts the external application secrets, completely automating the deployment process.

## Task 4: Infrastructure as Code (IaC) for Virtual Machines
*   **Goal:** Finalize ARM/Bicep templates for deployment to Azure Virtual Machine Scale Sets (VMSS).
*   **Definition:**
    1. Write a Bicep template `nanogate-vmss.bicep` that provisions a Virtual Machine Scale Set.
    2. Write cloud-init scripts to install Java 21 and run the NanoGate JAR as a systemd service.
    3. Configure an Azure Load Balancer to route traffic to the VMSS instances.
    4. Set up auto-scaling rules based on CPU percentage in the Bicep template.
*   **Use Case:** A customer who prefers IaaS over Kubernetes triggers a GitHub Action that uses the Bicep template to automatically build a scalable VM cluster in Azure, install the Java runtime, and configure the NanoGate application to start on boot behind an Azure Load Balancer.

## Task 5: Performance Benchmarking & Load Testing
*   **Goal:** Conduct rigorous load testing to prove the "low overhead" and horizontal scaling requirements under massive traffic spikes.
*   **Definition:**
    1. Write a Gatling or JMeter load testing script simulating concurrent high-throughput API traffic.
    2. Deploy the gateway and a mock backend to a staging environment.
    3. Run the load test and measure P99 latency and overhead introduced by NanoGate (Target: < 5ms overhead).
    4. Profile the application under load using async-profiler or Java Flight Recorder to detect memory leaks and lock contentions.
    5. Optimize garbage collection (ZGC/G1GC) and Virtual Thread configuration based on profiling data.
*   **Use Case:** Before a Black Friday sale, the engineering team runs the load testing suite. The tests verify that the gateway can smoothly handle 10,000 requests per second with less than 5 milliseconds of added latency, validating production readiness.

## Task 6: Operational Documentation & Runbooks
*   **Goal:** Complete operational runbooks, architecture diagrams, and developer guides.
*   **Definition:**
    1. Write a `TROUBLESHOOTING.md` runbook covering common production issues (e.g., backend timeouts, out of memory, config sync failures).
    2. Write a `DEPLOYMENT_GUIDE.md` for both AKS and VMSS environments.
    3. Finalize architecture diagrams using Mermaid JS in the main `ARCHITECTURE.md` file.
    4. Clean up code comments and generate comprehensive JavaDoc for the core routing APIs.
*   **Use Case:** A new Site Reliability Engineer is paged at 3:00 AM because of a failing route. They open the `TROUBLESHOOTING.md` runbook and immediately find step-by-step instructions on how to reload the hot-swappable routing configuration and inspect the circuit breaker metrics.

## Task 7: Verification & Pre-Production Sign-off
*   **Goal:** Final validation before moving to Azure Marketplace publication.
*   **Definition:**
    1. Execute the full suite of integration tests in a CI pipeline.
    2. Run SAST (Static Application Security Testing) tools on the codebase (e.g., SonarQube).
    3. Ensure 0 critical or high vulnerabilities in third-party dependencies (Dependabot/OWASP Dependency-Check).
    4. Final code review and merge to the main branch.
*   **Use Case:** The release manager requires a final sign-off before tagging a 1.0.0 release. The automated CI pipelines ensure that test coverage is high, code quality is exceptional, and there are absolutely no known security vulnerabilities in the gateway.
