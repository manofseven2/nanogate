package com.nanogate.routing.service.discovery;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.DiscoveryType;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Collections;
import java.util.List;

/**
 * The default provider for STATIC discovery type.
 * Simply returns the hardcoded list of servers from the configuration file.
 * This is perfect for Server-Side Load Balanced endpoints like standard Kubernetes Services.
 */
@Component
public class StaticServiceDiscoveryProvider implements ServiceDiscoveryProvider {

    @Override
    public boolean supports(DiscoveryType type) {
        return type == DiscoveryType.STATIC;
    }

    @Override
    public List<URI> discover(BackendSet backendSet) {
        List<URI> servers = backendSet.getServers();
        return servers != null ? servers : Collections.emptyList();
    }
}
