package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.nanogate.routing.NanoGateRoutingTestApp;
import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.BackendSet;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
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

    private static WireMockServer backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2;

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

        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2).forEach(WireMockServer::start);
    }

    @AfterAll
    static void stopServers() {
        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2).forEach(s -> {
            if (s != null) s.stop();
        });
    }

    @BeforeEach
    void resetWireMock() {
        List.of(backend1, backend2, backend3, slowBackend, leastConnBackend1, leastConnBackend2, healthCheckBackend1, healthCheckBackend2).forEach(WireMockServer::resetAll);
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testExactRoute_HappyPath() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/exact"))
                .willReturn(aResponse().withStatus(200).withHeader("Content-Type", "application/json").withBody("{\"message\": \"success from backend 1\"}")));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/exact")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"message\": \"success from backend 1\"}", response.body());
    }

    @Test
    void testExactRoute_PostWithBody() throws Exception {
        String requestBody = "{\"data\": \"test post request\"}";
        String responseBody = "{\"status\": \"created successfully\"}";

        backend1.stubFor(post(urlEqualTo("/api/exact")).withRequestBody(equalToJson(requestBody))
                .willReturn(aResponse().withStatus(201).withHeader("Content-Type", "application/json").withBody(responseBody)));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/exact")).header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(requestBody)).build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(201, response.statusCode());
        assertEquals(responseBody, response.body());
        backend1.verify(1, postRequestedFor(urlEqualTo("/api/exact")).withRequestBody(equalToJson(requestBody)));
    }

    @Test
    void testLoadBalancedRoute_RoundRobin() throws Exception {
        backend2.stubFor(get(urlPathMatching("/api/lb/.*")).willReturn(aResponse().withStatus(200).withBody("backend-2")));
        backend3.stubFor(get(urlPathMatching("/api/lb/.*")).willReturn(aResponse().withStatus(200).withBody("backend-3")));

        int backend2Count = 0;
        int backend3Count = 0;
        for (int i = 0; i < 4; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/lb/test")).GET().build();
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
        slowBackend.stubFor(get(urlEqualTo("/api/slow")).willReturn(aResponse().withFixedDelay(500).withStatus(200)));
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/slow")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertTrue(response.statusCode() >= 500);
    }

    @Test
    void testNoRouteMatched_Returns404() throws Exception {
        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/does-not-exist")).GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(404, response.statusCode());
    }

    @Test
    void testHeadersArePassedCorrectly() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/exact")).withHeader("X-Custom-Req", equalTo("test-val"))
                .willReturn(aResponse().withStatus(200).withHeader("X-Custom-Resp", "resp-val")));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/exact")).header("X-Custom-Req", "test-val").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.headers().map().containsKey("x-custom-resp"));
        assertEquals("resp-val", response.headers().firstValue("x-custom-resp").orElse(""));
    }

    @Test
    void testLeastConnectionsLoadBalancer() throws Exception {
        leastConnBackend1.stubFor(get(urlEqualTo("/api/lc/item")).willReturn(aResponse().withFixedDelay(1000).withStatus(200).withBody("slow-backend-1")));
        leastConnBackend2.stubFor(get(urlEqualTo("/api/lc/item")).willReturn(aResponse().withStatus(200).withBody("fast-backend-2")));

        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/lc/item")).GET().build();
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

        healthCheckBackend1.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        healthCheckBackend2.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
        healthCheckService.checkServerHealth(unhealthyUri, healthProps).join();
        healthCheckService.checkServerHealth(healthyUri, healthProps).join();
        assertTrue(healthCheckService.isHealthy(unhealthyUri), "Server should be healthy initially");

        healthCheckBackend1.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(503)));
        healthCheckService.checkServerHealth(unhealthyUri, healthProps).join();
        assertFalse(healthCheckService.isHealthy(unhealthyUri), "Server should now be unhealthy");

        healthCheckBackend1.stubFor(get(urlEqualTo("/api/health-test/data")).willReturn(aResponse().withBody("backend-1-unhealthy")));
        healthCheckBackend2.stubFor(get(urlEqualTo("/api/health-test/data")).willReturn(aResponse().withBody("backend-2-healthy")));

        int backend1Count = 0;
        int backend2Count = 0;
        for (int i = 0; i < 4; i++) {
            HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/health-test/data")).GET().build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            if ("backend-1-unhealthy".equals(response.body())) backend1Count++;
            if ("backend-2-healthy".equals(response.body())) backend2Count++;
        }
        assertEquals(0, backend1Count, "Unhealthy server should receive no traffic");
        assertEquals(4, backend2Count, "Healthy server should receive all traffic");
    }

    @Test
    void testHeaderTransformationRoute() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/headers/test"))
                .withHeader("X-Gateway-Source", equalTo("NanoGate"))
                .willReturn(aResponse().withStatus(200).withHeader("X-Powered-By", "Some-Framework").withBody("{\"headers\":\"ok\"}")));

        HttpRequest request = HttpRequest.newBuilder().uri(URI.create(getBaseUrl() + "/api/headers/test")).header("X-Internal-Debug", "some-value").GET().build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertFalse(response.headers().firstValue("X-Powered-By").isPresent(), "X-Powered-By header should have been removed");
        assertTrue(response.headers().firstValue("X-Content-Source").isPresent(), "X-Content-Source header should have been added");
        assertEquals("Backend", response.headers().firstValue("X-Content-Source").get());

        List<LoggedRequest> requests = backend1.findAll(getRequestedFor(urlEqualTo("/api/headers/test")));
        assertEquals(1, requests.size());
        LoggedRequest loggedRequest = requests.get(0);
        assertTrue(loggedRequest.containsHeader("X-Gateway-Source"), "Backend should have received X-Gateway-Source");
        assertFalse(loggedRequest.containsHeader("X-Internal-Debug"), "Backend should not have received X-Internal-Debug");
    }
}
