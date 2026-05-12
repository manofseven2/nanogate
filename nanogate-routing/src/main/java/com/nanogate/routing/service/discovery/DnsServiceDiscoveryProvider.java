package com.nanogate.routing.service.discovery;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.DiscoveryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Resolves a hostname directly via DNS A-Records.
 * Essential for Kubernetes Headless Services where K8s internal DNS returns multiple Pod IPs
 * and allows NanoGate to natively perform Client-Side Load Balancing.
 */
@Component
public class DnsServiceDiscoveryProvider implements ServiceDiscoveryProvider {

    private static final Logger log = LoggerFactory.getLogger(DnsServiceDiscoveryProvider.class);

    @Override
    public boolean supports(DiscoveryType type) {
        return type == DiscoveryType.DNS;
    }

    @Override
    public List<URI> discover(BackendSet backendSet) {
        String serviceId = backendSet.getServiceId(); // Expected format: hostname:port
        if (serviceId == null || serviceId.isBlank()) {
            log.error("BackendSet '{}' is configured for DNS discovery but is missing a service-id (expected host:port).", backendSet.getName());
            return Collections.emptyList();
        }

        String host = serviceId;
        int port = 80; // default HTTP port

        if (serviceId.contains(":")) {
            String[] parts = serviceId.split(":");
            host = parts[0];
            try {
                port = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                log.warn("Invalid port in service-id '{}'. Defaulting to 80.", serviceId);
            }
        }

        try {
            InetAddress[] addresses = InetAddress.getAllByName(host);
            List<URI> uris = new ArrayList<>(addresses.length);
            for (InetAddress address : addresses) {
                // Determine if we need HTTP or HTTPS based on the port or defaults (simplifying to http for native IP routing)
                String scheme = (port == 443 || port == 8443) ? "https" : "http";
                uris.add(URI.create(scheme + "://" + address.getHostAddress() + ":" + port));
            }
            log.debug("DNS resolved {} IPs for host: {}", addresses.length, host);
            return uris;
        } catch (UnknownHostException e) {
            log.warn("DNS resolution failed for host: {}. Returning empty list.", host);
            return Collections.emptyList();
        }
    }
}
