package com.nanogate.routing.model;

/**
 * Defines how NanoGate discovers the backend instances for a given BackendSet.
 */
public enum DiscoveryType {
    /**
     * Standard Server-Side Load Balancing. 
     * Uses the static `servers` list defined in configuration. 
     * Works perfectly for legacy hardware LBs or Kubernetes Services where kube-proxy handles the LB.
     */
    STATIC,

    /**
     * Natively resolves DNS A/SRV records.
     * Perfect for Kubernetes Headless Services where the DNS returns a list of Pod IPs directly,
     * allowing NanoGate to perform Client-Side Load Balancing across them.
     */
    DNS,

    /**
     * Natively queries a HashiCorp Consul catalog using its HTTP API.
     * Extremely lightweight, requiring zero heavy Spring Cloud dependencies.
     */
    CONSUL
}
