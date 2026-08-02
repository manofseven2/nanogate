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
    4. Implement semantic versioning (SemVer) and automated Git tagging for releases. we should have a release step manually we push it to make a new release and push and the release notes should be populated using commit history. We have to define in advance what type of release we want to make (patch, minor, major). 
*   **Use Case:** A developer submits a pull request. The CI pipeline automatically spins up, runs all Unit and Integration tests, and reports success. Once merged, the CD pipeline builds a hardened Docker image and pushes it to the registry, ready for deployment to AKS or VMSS.

## Task 3: Infrastructure as Code (IaC) - Docker Compose (The Budget / Hobbyist Path)
*   **Goal:** Provide the absolute lowest-cost deployment option for single-node Virtual Private Servers (VPS) using Docker Compose, complete with the observability stack.
*   **Definition:**
    1. Create a production-ready `docker-compose.yml` in the root directory.
    2. Configure the compose file to spin up NanoGate, Prometheus, and Grafana simultaneously.
    3. Ensure data persistence for Prometheus metrics via local Docker volumes.
*   **Use Case:** A solo developer or small startup wants to run NanoGate on a $5/month DigitalOcean Droplet or AWS Lightsail instance. They simply clone the repo and run `docker-compose up -d` to instantly get a working gateway with a Grafana dashboard.

## Task 4: Performance Benchmarking & Load Testing
*   **Goal:** Conduct rigorous load testing to prove the "low overhead" and horizontal scaling requirements under massive traffic spikes across the deployment environments.
*   **Definition:**
    1. Write a Gatling or JMeter load testing script simulating concurrent high-throughput API traffic.
    2. Deploy the gateway and a mock backend locally using Docker Compose (Task 3).
    3. Run the load test and measure P99 latency and overhead introduced by NanoGate (Target: < 5ms overhead).
    4. Profile the application under load using async-profiler or Java Flight Recorder to detect memory leaks and lock contentions.
    5. Publish benchmarking results in the documentation to demonstrate performance parity.
*   **Use Case:** Before adopting the project, a technical lead reviews the benchmark documentation. The tests verify that the gateway can smoothly handle 10,000 requests per second with less than 5 milliseconds of added latency.

## Task 5: Infrastructure as Code (IaC) - Virtual Machine Clusters (The Standard Production Path)
*   **Goal:** Provide "1-Click" deployment templates for highly available, cost-effective Virtual Machine (IaaS) clusters on both AWS and Azure.
*   **Definition:**
    1. Write an **AWS CloudFormation** template (`aws-ec2-asg.yaml`) that deploys NanoGate to an EC2 Auto Scaling Group using cheap burstable instances (e.g., `t4g.small`).
    2. Write an **Azure Bicep/ARM** template (`azure-vmss.bicep`) that deploys NanoGate to an Azure Virtual Machine Scale Set (VMSS) using `B-series` instances.
    3. Ensure these templates provision a centralized Grafana/Prometheus instance within the same Virtual Network.
    4. Provide "Launch Stack" (AWS) and "Deploy to Azure" buttons directly in the `README.md`.
*   **Use Case:** A mid-sized team clicks "Deploy to AWS" in the README. The template provisions a robust, auto-scaling VM cluster for under $30/month, alongside a pre-configured Grafana dashboard for a consistent "single pane of glass" view.

## Task 6: Infrastructure as Code (IaC) - Kubernetes & Helm (The Enterprise Path)
*   **Goal:** Finalize standard Helm charts for seamless deployment to any Kubernetes cluster (AWS EKS, Azure AKS, on-prem) for enterprise users who already run K8s.
*   **Definition:**
    1. Create a `helm/nanogate` directory structure.
    2. Create Kubernetes `Deployment`, `Service`, `ConfigMap`, and `HPA` templates.
    3. Support standard Kubernetes Secrets for sensitive configuration.
    4. Bundle a pre-configured `kube-prometheus-stack` dependency in the Helm chart to ensure dashboards and metrics deploy alongside the gateway.
*   **Use Case:** A DevOps engineer uses `helm install nanogate ./helm/nanogate` in a CI/CD pipeline. The chart instantly spins up the gateway pods on their existing EKS cluster, configuring auto-scaling and integrating with their existing Prometheus setup.

## Task 7: Operational Documentation & Runbooks
*   **Goal:** Complete operational runbooks, architecture diagrams, and developer guides tailored for the multi-cloud, open-source community.
*   **Definition:**
    1. Write a `TROUBLESHOOTING.md` runbook covering common production issues (e.g., backend timeouts, out of memory, config sync failures).
    2. Write a `DEPLOYMENT_GUIDE.md` providing step-by-step instructions for Docker Compose, AWS (EC2/EKS), and Azure (VMSS/AKS).
    3. Finalize architecture diagrams using Mermaid JS in the main `ARCHITECTURE.md` file.
    4. Clean up code comments and generate comprehensive JavaDoc.
*   **Use Case:** An SRE is paged because of a failing route. They open the `TROUBLESHOOTING.md` runbook and immediately find step-by-step instructions on how to reload the hot-swappable routing configuration.

## Task 8: Verification & Open-Source 1.0 Release
*   **Goal:** Final validation before officially launching NanoGate 1.0 to the open-source community.
*   **Definition:**
    1. Execute the full suite of integration tests in a CI pipeline.
    2. Run SAST tools on the codebase (e.g., SonarQube).
    3. Ensure 0 critical or high vulnerabilities in third-party dependencies.
    4. Publish the official 1.0 Docker image to DockerHub and GitHub Container Registry.
    5. Draft and publish the official GitHub Release notes.
*   **Use Case:** The maintainer approves the final PR. The automated CD pipelines ensure the public Docker images are built and pushed, and the 1-click deploy templates are pointing to a stable 1.0 release.
