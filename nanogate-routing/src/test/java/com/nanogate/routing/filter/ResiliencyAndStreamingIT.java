package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nanogate.routing.NanoGateRoutingTestApp;
import com.nanogate.routing.service.HealthCheckService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
class ResiliencyAndStreamingIT {

    @LocalServerPort
    private int localPort;

    @Autowired
    private HealthCheckService healthCheckService;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static WireMockServer backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2, streamingBackend;

    @BeforeAll
    static void startServers() {
        backend1 = new WireMockServer(WireMockConfiguration.options().port(8081));
        backend2 = new WireMockServer(WireMockConfiguration.options().port(8082));
        backend3 = new WireMockServer(WireMockConfiguration.options().port(8083));
        slowBackend = new WireMockServer(WireMockConfiguration.options().port(8084));
        leastConnBackend1 = new WireMockServer(WireMockConfiguration.options().port(8085));
        leastConnBackend2 = new WireMockServer(WireMockConfiguration.options().port(8086));
        healthCheckBackend1 = new WireMockServer(WireMockConfiguration.options().port(8088));
        healthCheckBackend2 = new WireMockServer(WireMockConfiguration.options().port(8089));
        streamingBackend = new WireMockServer(WireMockConfiguration.options().port(8090));

        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2, streamingBackend).forEach(WireMockServer::start);
    }

    @AfterAll
    static void stopServers() {
        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2, streamingBackend).forEach(s -> {
            if (s != null) s.stop();
        });
    }

    @BeforeEach
    void resetTests() {
        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2, streamingBackend).forEach(WireMockServer::resetAll);
        
        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2, streamingBackend).forEach(s -> {
            s.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
            s.stubFor(get(urlEqualTo("/specific-health")).willReturn(aResponse().withStatus(200)));
            try {
                ((com.nanogate.routing.service.ActiveHealthCheckService) healthCheckService).checkServerHealth(new URI("http://localhost:" + s.port()), new com.nanogate.routing.model.HealthCheckProperties("/health", null, null)).join();
            } catch (Exception e) {
                // Ignore
            }
        });
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testStreamingProxy_HandlesLargePayload() throws Exception {
        int bodySize = 10 * 1024 * 1024;
        byte[] largeBody = new byte[bodySize];
        new Random().nextBytes(largeBody);

        streamingBackend.stubFor(get(urlEqualTo("/api/streaming/large-file"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/octet-stream").withBody(largeBody)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/streaming/large-file"))
                .timeout(Duration.ofSeconds(30))
                .GET()
                .build();

        HttpResponse<InputStream> response = httpClient.send(request, HttpResponse.BodyHandlers.ofInputStream());

        assertEquals(200, response.statusCode());

        long bytesRead = 0;
        try (InputStream bodyStream = response.body()) {
            byte[] buffer = new byte[8192];
            int bytes;
            while ((bytes = bodyStream.read(buffer)) != -1) {
                bytesRead += bytes;
            }
        }
        assertEquals(bodySize, bytesRead, "The full large payload should have been streamed");
    }

    @Test
    void testCircuitBreaker_OpensOnFailureAndReroutesTraffic() throws Exception {
        backend3.stubFor(get(urlEqualTo("/api/lb/test")).willReturn(aResponse().withStatus(200).withBody("backend-3")));
        backend2.stubFor(get(urlEqualTo("/api/lb/test")).willReturn(aResponse().withStatus(503)));

        for (int i = 0; i < 20; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/lb/test")).GET().build();
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        }

        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/lb/test")).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "Request should succeed and be rerouted to the healthy backend");
            assertEquals("backend-3", response.body());
        }
    }

    @Test
    void testResponse_ExceedsLimit_Returns502() throws Exception {
        int oversized = 2 * 1024 * 1024; // 2MB
        streamingBackend.stubFor(get(urlEqualTo("/api/streaming/oversized"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Length", String.valueOf(oversized))
                        .withBody(new byte[oversized])));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/streaming/oversized"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(502, response.statusCode(), "Gateway should return 502 Bad Gateway for oversized upstream response");
    }
}
