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
class RateLimitFilterIT {

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
    void testIpBasedRateLimiting() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/ratelimit/test"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/ratelimit/test"))
                .header("X-Forwarded-For", "192.168.1.50")
                .GET()
                .build();

        // 1st request should pass (limit is 2 req/sec)
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response1.statusCode());

        // 2nd request should pass
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response2.statusCode());

        // 3rd request should hit the rate limit and fail with 429
        HttpResponse<String> response3 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, response3.statusCode());
        
        // Request from a different IP should still pass (separate bucket)
        HttpRequest requestDifferentIp = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/ratelimit/test"))
                .header("X-Forwarded-For", "10.0.0.99")
                .GET()
                .build();
        HttpResponse<String> response4 = httpClient.send(requestDifferentIp, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response4.statusCode());
    }
    
    @Test
    void testHeaderBasedRateLimiting() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/header-ratelimit/test"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest requestUserA = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/header-ratelimit/test"))
                .header("X-API-Key", "userA")
                .GET()
                .build();

        // User A 1st request should pass (limit is 1 req/sec)
        HttpResponse<String> responseA1 = httpClient.send(requestUserA, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, responseA1.statusCode());

        // User A 2nd request should fail with 429
        HttpResponse<String> responseA2 = httpClient.send(requestUserA, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, responseA2.statusCode());

        // User B request should pass (separate bucket)
        HttpRequest requestUserB = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/header-ratelimit/test"))
                .header("X-API-Key", "userB")
                .GET()
                .build();
        HttpResponse<String> responseB1 = httpClient.send(requestUserB, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, responseB1.statusCode());
    }
}
