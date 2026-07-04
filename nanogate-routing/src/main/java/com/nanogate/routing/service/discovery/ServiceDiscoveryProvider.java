package com.nanogate.routing.service.discovery;

import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.DiscoveryType;

import java.net.URI;
import java.util.List;

/**
 * Strategy interface for dynamically resolving a BackendSet into a list of accessible server URIs.
 */
public interface ServiceDiscoveryProvider {
    
    /**
     * @param type The configured discovery type for a BackendSet.
     * @return true if this provider implements the resolution for the given type.
     */
    boolean supports(DiscoveryType type);

    /**
     * Resolves the current list of available server instances.
     *
     * @param backendSet The backend set containing the serviceId and configuration.
     * @return A list of URIs representing the accessible endpoints.
     */
    List<URI> discover(BackendSet backendSet);
}
