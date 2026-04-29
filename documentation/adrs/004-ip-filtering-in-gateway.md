# ADR 004: Implementing Application-Layer IP Filtering

## Status
Accepted

## Context
NanoGate is designed to operate in cloud environments where it is often deployed behind network-level security appliances, such as Web Application Firewalls (WAFs) or Network Security Groups (NSGs). These infrastructure components are highly optimized for dropping malicious traffic, mitigating DDoS attacks, and enforcing broad network restrictions at the edge. 

However, relying exclusively on infrastructure-level firewalls limits the ability to enforce fine-grained, application-aware security policies. Modern API management often requires security policies that depend on both the context of the specific API route being accessed and the business logic associated with the tenant.

## Decision
We will implement native IP Allowlisting and Blocklisting capabilities directly within the `nanogate-security` module. This feature will operate at the application layer (Layer 7) as part of the gateway's request filter chain.

## Consequences

### Positive
*   **Tenant-Specific Rules:** NanoGate can enforce granular access controls, such as ensuring specific API keys or tenant contexts are only valid when requests originate from their registered IP addresses.
*   **Route-Specific Rules:** Administrators can restrict highly sensitive internal routes (e.g., `/admin/metrics`) to corporate VPN IPs, while leaving public routes open, without maintaining complex configurations in an external WAF.
*   **Defense in Depth:** Application-layer IP filtering provides a secondary security perimeter, protecting the application even if upstream network firewalls are misconfigured or bypassed.
*   **Self-Contained Security:** Deployments in simpler environments that lack a dedicated WAF will still benefit from essential threat protection out of the box.

### Negative
*   **Processing Overhead:** Evaluating IP access rules adds a slight processing overhead to every incoming request. This will be mitigated by placing the filter early in the chain and using highly efficient IP parsing and matching algorithms.
*   **Configuration Overlap:** There is a potential for configuration overlap between the gateway and upstream firewalls. Clear documentation must establish that NanoGate's IP filtering is intended for application-specific business logic, while broad network blocking should remain at the infrastructure edge.
