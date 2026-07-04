package com.nanogate.routing.service.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.ConsulDiscoveryProperties;
import com.nanogate.routing.model.DiscoveryType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A lightweight, native implementation of Consul Service Discovery using the HTTP API.
 * This avoids the need for heavy spring-cloud-commons dependencies while providing
 * robust service resolution.
 */
@Component
public class ConsulServiceDiscoveryProvider implements ServiceDiscoveryProvider {

    private static final Logger log = LoggerFactory.getLogger(ConsulServiceDiscoveryProvider.class);

    private final NanoGateRouteProperties routeProperties;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public ConsulServiceDiscoveryProvider(NanoGateRouteProperties routeProperties) {
        this.routeProperties = routeProperties;
        this.mapper = new ObjectMapper();
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public boolean supports(DiscoveryType type) {
        return type == DiscoveryType.CONSUL;
    }

    @Override
    public List<URI> discover(BackendSet backendSet) {
        String serviceId = backendSet.getServiceId();
        if (serviceId == null || serviceId.isBlank()) {
            log.error("BackendSet '{}' is configured for CONSUL discovery but is missing a service-id.", backendSet.getName());
            return Collections.emptyList();
        }

        ConsulDiscoveryProperties consulProps = routeProperties.getDiscovery().getConsul();
        String consulUrl = String.format("http://%s:%d/v1/catalog/service/%s", consulProps.getHost(), consulProps.getPort(), serviceId);

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(consulUrl))
                    .timeout(Duration.ofSeconds(2))
                    .GET()
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                log.warn("Consul catalog request for {} returned status code {}", serviceId, response.statusCode());
                return Collections.emptyList();
            }

            JsonNode rootNode = mapper.readTree(response.body());
            List<URI> uris = new ArrayList<>();

            if (rootNode.isArray()) {
                for (JsonNode node : rootNode) {
                    String address = node.path("ServiceAddress").asText();
                    if (address.isBlank()) {
                        // Fallback to node address if ServiceAddress is not provided
                        address = node.path("Address").asText();
                    }
                    int port = node.path("ServicePort").asInt(80);
                    
                    // Defaulting to HTTP scheme for catalog services
                    uris.add(URI.create("http://" + address + ":" + port));
                }
            }

            log.debug("Consul Discovery resolved {} instances for serviceId: {}", uris.size(), serviceId);
            return uris;

        } catch (Exception e) {
            log.error("Failed to query Consul at {} for service {}", consulUrl, serviceId, e);
            return Collections.emptyList();
        }
    }
}
