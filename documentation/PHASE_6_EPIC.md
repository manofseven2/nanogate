# Phase 6: Cloud-Native Extensions

**Goal:** Extend NanoGate with managed cloud services to support enterprise-grade deployments on Azure and other cloud providers.

## Task 1: Azure App Configuration Integration
*   **Goal:** Use Azure App Configuration as the primary source of truth for routing and resilience rules.
*   **Definition:**
    1. Implement `AzureAppConfigurationProvider` in `nanogate-routing` using the `spring-cloud-azure-starter-appconfiguration` dependency.
    2. Support automatic secret resolution via **Azure Key Vault** for backend URLs containing sensitive tokens.
    3. Configure a "Sentinel" key in Azure to trigger the `ConfigurationRefreshService` when changes are published.
*   **Use Case:** An SRE updates a route path in the Azure Portal. The change is propagated to all running NanoGate instances globally within seconds without a restart.

## Task 2: Azure Managed Identity & Key Vault
*   **Goal:** Securely manage gateway secrets and service-to-service credentials without storing keys in the codebase.
*   **Definition:**
    1. Integrate Azure Identity for passwordless connection to Redis and Azure App Config.
    2. Implement automatic rotation of certificates used for JWT signing or TLS termination.
