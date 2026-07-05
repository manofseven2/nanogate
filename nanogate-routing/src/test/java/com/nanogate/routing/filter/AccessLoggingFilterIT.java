package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nanogate.routing.NanoGateRoutingTestApp;
import com.nanogate.routing.model.HealthCheckProperties;
import com.nanogate.routing.service.ActiveHealthCheckService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
class AccessLoggingFilterIT {

    @LocalServerPort
    private int localPort;

    @Autowired
    private ActiveHealthCheckService healthCheckService;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static WireMockServer backend1;

    @BeforeAll
    static void startServers() {
        backend1 = new WireMockServer(WireMockConfiguration.options().port(8081));
        backend1.start();
    }

    @AfterAll
    static void stopServers() {
        if (backend1 != null) {
            backend1.stop();
        }
    }

    @BeforeEach
    void resetWireMock() throws Exception {
        backend1.resetAll();
        backend1.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        
        // Manually trigger health check to ensure backend is marked UP
        healthCheckService.checkServerHealth(
                new URI("http://localhost:8081"),
                new HealthCheckProperties("/health", null, null)
        ).join();
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testAccessLoggingDoesNotDisruptRouting() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/exact"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .header("X-Forwarded-For", "192.168.1.50")
                .GET()
                .build();

        // The request should pass successfully, and the AccessLoggingFilter should log it without breaking the chain.
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertEquals("ok", response.body());
    }
}
