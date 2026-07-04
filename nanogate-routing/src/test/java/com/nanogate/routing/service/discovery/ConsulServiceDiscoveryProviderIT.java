package com.nanogate.routing.service.discovery;

import com.nanogate.routing.NanoGateRoutingTestApp;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.DiscoveryType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NanoGateRoutingTestApp.class)
@Testcontainers
class ConsulServiceDiscoveryProviderIT {

    @Container
    static GenericContainer<?> consul = new GenericContainer<>(DockerImageName.parse("hashicorp/consul:latest"))
            .withExposedPorts(8500)
            .withCommand("agent -dev -client 0.0.0.0");

    @DynamicPropertySource
    static void consulProperties(DynamicPropertyRegistry registry) {
        registry.add("nanogate.routing.discovery.consul.host", consul::getHost);
        registry.add("nanogate.routing.discovery.consul.port", consul::getFirstMappedPort);
    }

    @Autowired
    private ConsulServiceDiscoveryProvider provider;

    @BeforeAll
    static void setupConsulData() throws Exception {
        // Register a mock service in Consul
        String registerJson = "{" +
                "\"ID\": \"mock-user-service-1\"," +
                "\"Name\": \"mock-user-service\"," +
                "\"Address\": \"127.0.0.1\"," +
                "\"Port\": 8081" +
                "}";

        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + consul.getHost() + ":" + consul.getFirstMappedPort() + "/v1/agent/service/register"))
                .PUT(HttpRequest.BodyPublishers.ofString(registerJson))
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
    }

    @Test
    void shouldDiscoverServiceFromConsul() {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("user-service");
        backendSet.setDiscoveryType(DiscoveryType.CONSUL);
        backendSet.setServiceId("mock-user-service");

        List<URI> uris = provider.discover(backendSet);

        assertThat(uris).hasSize(1);
        assertThat(uris.get(0)).isEqualTo(URI.create("http://127.0.0.1:8081"));
    }

    @Test
    void shouldReturnEmptyListForUnknownService() {
        BackendSet backendSet = new BackendSet();
        backendSet.setName("unknown");
        backendSet.setDiscoveryType(DiscoveryType.CONSUL);
        backendSet.setServiceId("unknown-service");

        List<URI> uris = provider.discover(backendSet);

        assertThat(uris).isEmpty();
    }
}
