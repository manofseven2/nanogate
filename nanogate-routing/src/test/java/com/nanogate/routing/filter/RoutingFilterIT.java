package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nanogate.routing.NanoGateRoutingTestApp;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
import com.nanogate.routing.model.HealthCheckProperties;
import com.nanogate.routing.service.ActiveHealthCheckService;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it") // Loads application-it.yml
class RoutingFilterIT {

    @LocalServerPort
    private int localPort;

    @Autowired
    private ActiveHealthCheckService healthCheckService;

    @Autowired
    private NanoGateRouteProperties routeProperties;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static WireMockServer backend1;
    private static WireMockServer backend2;
    private static WireMockServer backend3;
    private static WireMockServer slowBackend;
    private static WireMockServer leastConnBackend1;
    private static WireMockServer leastConnBackend2;
    private static WireMockServer healthCheckBackend1;
    private static WireMockServer healthCheckBackend2;

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

        backend1.start();
        backend2.start();
        backend3.start();
        slowBackend.start();
        leastConnBackend1.start();
        leastConnBackend2.start();
        healthCheckBackend1.start();
        healthCheckBackend2.start();
    }

    @AfterAll
    static void stopServers() {
        if (backend1 != null) backend1.stop();
        if (backend2 != null) backend2.stop();
        if (backend3 != null) backend3.stop();
        if (slowBackend != null) slowBackend.stop();
        if (leastConnBackend1 != null) leastConnBackend1.stop();
        if (leastConnBackend2 != null) leastConnBackend2.stop();
        if (healthCheckBackend1 != null) healthCheckBackend1.stop();
        if (healthCheckBackend2 != null) healthCheckBackend2.stop();
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testExactRoute_HappyPath() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/exact"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/json")
                        .withBody("{\"message\": \"success from backend 1\"}")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"message\": \"success from backend 1\"}", response.body());
    }
    
    @Test
    void testExactRoute_PostWithBody() throws Exception {
        String requestBody = "{\"data\": \"test post request\"}";
        String responseBody = "{\"status\": \"created successfully\"}";

        backend1.stubFor(post(urlEqualTo("/api/exact"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals(responseBody, response.body());
        
        backend1.verify(1, postRequestedFor(urlEqualTo("/api/exact"))
                .withRequestBody(equalToJson(requestBody)));
    }

    @Test
    void testLoadBalancedRoute_RoundRobin() throws Exception {
        backend2.stubFor(get(urlPathMatching("/api/lb/.*"))
                .willReturn(aResponse().withStatus(200).withBody("backend-2")));
        backend3.stubFor(get(urlPathMatching("/api/lb/.*"))
                .willReturn(aResponse().withStatus(200).withBody("backend-3")));

        int backend2Count = 0;
        int backend3Count = 0;

        for (int i = 0; i < 4; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/lb/test"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertEquals(200, response.statusCode());
            if ("backend-2".equals(response.body())) backend2Count++;
            if ("backend-3".equals(response.body())) backend3Count++;
        }

        assertEquals(2, backend2Count);
        assertEquals(2, backend3Count);
    }

    @Test
    void testSlowRoute_TimeoutOverride() throws Exception {
        slowBackend.stubFor(get(urlEqualTo("/api/slow"))
                .willReturn(aResponse()
                        .withFixedDelay(500)
                        .withStatus(200)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/slow"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertTrue(response.statusCode() >= 500);
    }

    @Test
    void testNoRouteMatched_Returns404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/does-not-exist"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(404, response.statusCode());
    }

    @Test
    void testHeadersArePassedCorrectly() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/exact"))
                .withHeader("X-Custom-Req", equalTo("test-val"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("X-Custom-Resp", "resp-val")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .header("X-Custom-Req", "test-val")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().map().containsKey("x-custom-resp"));
        assertEquals("resp-val", response.headers().firstValue("x-custom-resp").orElse(""));
    }

    @Test
    void testLeastConnectionsLoadBalancer() throws Exception {
        leastConnBackend1.stubFor(get(urlEqualTo("/api/lc/item"))
                .willReturn(aResponse()
                        .withFixedDelay(1000)
                        .withStatus(200)
                        .withBody("slow-backend-1")));

        leastConnBackend2.stubFor(get(urlEqualTo("/api/lc/item"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("fast-backend-2")));

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/lc/item"))
                    .GET()
                    .build();
            futures.add(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            
            Thread.sleep(50);
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int backend1Count = 0;
        int backend2Count = 0;

        for (CompletableFuture<HttpResponse<String>> future : futures) {
            HttpResponse<String> response = future.get();
            assertEquals(200, response.statusCode());
            if ("slow-backend-1".equals(response.body())) backend1Count++;
            if ("fast-backend-2".equals(response.body())) backend2Count++;
        }

        assertTrue(backend2Count > backend1Count);
        assertEquals(1, backend1Count, "Slow backend should only handle 1 request");
        assertEquals(4, backend2Count, "Fast backend should handle the rest");
    }

    @Test
    void testHealthCheck_ExcludesUnhealthyServerFromLoadBalancing() throws Exception {
        final URI unhealthyUri = new URI("http://localhost:8088");
        final URI healthyUri = new URI("http://localhost:8089");
        
        BackendSet backendSet = routeProperties.getBackendSet("health-check-backend");
        HealthCheckProperties healthProps = backendSet.getHealthCheck();

        // 1. Start with both servers being healthy
        healthCheckBackend1.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        healthCheckBackend2.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));

        // 2. Manually run checks and wait for them to complete synchronously
        healthCheckService.checkServerHealth(unhealthyUri, healthProps).join();
        healthCheckService.checkServerHealth(healthyUri, healthProps).join();
        assertTrue(healthCheckService.isHealthy(unhealthyUri), "Server should be healthy initially");

        // 3. Now, make one server consistently fail its health check
        healthCheckBackend1.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(503)));

        // 4. Manually run the check again and wait for it to complete
        healthCheckService.checkServerHealth(unhealthyUri, healthProps).join();
        assertFalse(healthCheckService.isHealthy(unhealthyUri), "Server should now be unhealthy");

        // 5. Stub the actual API endpoints
        healthCheckBackend1.stubFor(get(urlEqualTo("/api/health-test/data")).willReturn(aResponse().withBody("backend-1-unhealthy")));
        healthCheckBackend2.stubFor(get(urlEqualTo("/api/health-test/data")).willReturn(aResponse().withBody("backend-2-healthy")));

        // 6. Send multiple requests. All should go to the healthy backend (backend-2).
        int backend1Count = 0;
        int backend2Count = 0;
        for (int i = 0; i < 4; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/health-test/data"))
                    .GET()
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            assertEquals(200, response.statusCode());
            if ("backend-1-unhealthy".equals(response.body())) backend1Count++;
            if ("backend-2-healthy".equals(response.body())) backend2Count++;
        }

        assertEquals(0, backend1Count, "Unhealthy server should receive no traffic");
        assertEquals(4, backend2Count, "Healthy server should receive all traffic");
    }
}
