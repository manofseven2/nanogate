package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nanogate.routing.NanoGateRoutingTestApp;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it") // Loads application-it.yml
class RoutingFilterIT {

    @LocalServerPort
    private int localPort;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static WireMockServer backend1;
    private static WireMockServer backend2;
    private static WireMockServer backend3;
    private static WireMockServer slowBackend;
    private static WireMockServer leastConnBackend1;
    private static WireMockServer leastConnBackend2;

    @BeforeAll
    static void startServers() {
        // Start WireMock servers on ports matching application-it.yml
        backend1 = new WireMockServer(WireMockConfiguration.options().port(8081));
        backend2 = new WireMockServer(WireMockConfiguration.options().port(8082));
        backend3 = new WireMockServer(WireMockConfiguration.options().port(8083));
        slowBackend = new WireMockServer(WireMockConfiguration.options().port(8084));
        leastConnBackend1 = new WireMockServer(WireMockConfiguration.options().port(8085));
        leastConnBackend2 = new WireMockServer(WireMockConfiguration.options().port(8086));

        backend1.start();
        backend2.start();
        backend3.start();
        slowBackend.start();
        leastConnBackend1.start();
        leastConnBackend2.start();
    }

    @AfterAll
    static void stopServers() {
        if (backend1 != null) backend1.stop();
        if (backend2 != null) backend2.stop();
        if (backend3 != null) backend3.stop();
        if (slowBackend != null) slowBackend.stop();
        if (leastConnBackend1 != null) leastConnBackend1.stop();
        if (leastConnBackend2 != null) leastConnBackend2.stop();
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

        // Stub a POST request that requires a specific body
        backend1.stubFor(post(urlEqualTo("/api/exact"))
                .withRequestBody(equalToJson(requestBody))
                .willReturn(aResponse()
                        .withStatus(201)
                        .withHeader("Content-Type", "application/json")
                        .withBody(responseBody)));

        // Send the POST request to NanoGate
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Verify NanoGate successfully proxied the body and returned the correct response
        assertEquals(201, response.statusCode());
        assertEquals(responseBody, response.body());
        
        // Verify wiremock actually received the exact POST request with body
        backend1.verify(1, postRequestedFor(urlEqualTo("/api/exact"))
                .withRequestBody(equalToJson(requestBody)));
    }

    @Test
    void testLoadBalancedRoute_RoundRobin() throws Exception {
        // Stub the same path on both backend servers in the backend-set
        backend2.stubFor(get(urlPathMatching("/api/lb/.*"))
                .willReturn(aResponse().withStatus(200).withBody("backend-2")));
        backend3.stubFor(get(urlPathMatching("/api/lb/.*"))
                .willReturn(aResponse().withStatus(200).withBody("backend-3")));

        // Send 4 requests
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

        // Verify Round Robin distributed them evenly (2 to each)
        assertEquals(2, backend2Count);
        assertEquals(2, backend3Count);
    }

    @Test
    void testSlowRoute_TimeoutOverride() throws Exception {
        // This backend takes 500ms to respond, but the route has a 100ms timeout override
        slowBackend.stubFor(get(urlEqualTo("/api/slow"))
                .willReturn(aResponse()
                        .withFixedDelay(500)
                        .withStatus(200)));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/slow"))
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Expecting a Bad Gateway or Gateway Timeout because the client aborted early
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
        assertTrue(response.headers().map().containsKey("X-Custom-Resp".toLowerCase())); // Java HttpClient headers are lowercase
        assertEquals("resp-val", response.headers().firstValue("X-Custom-Resp").orElse(""));
    }

    @Test
    void testLeastConnectionsLoadBalancer() throws Exception {
        // We configure leastConnBackend1 to be very slow, simulating high load
        leastConnBackend1.stubFor(get(urlEqualTo("/api/lc/item"))
                .willReturn(aResponse()
                        .withFixedDelay(1000)
                        .withStatus(200)
                        .withBody("slow-backend-1")));

        // We configure leastConnBackend2 to be very fast
        leastConnBackend2.stubFor(get(urlEqualTo("/api/lc/item"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withBody("fast-backend-2")));

        // Fire off multiple requests concurrently
        List<CompletableFuture<HttpResponse<String>>> futures = new ArrayList<>();
        
        for (int i = 0; i < 5; i++) {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(getBaseUrl() + "/api/lc/item"))
                    .GET()
                    .build();
            futures.add(httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString()));
            
            // tiny delay to ensure the first request hits the slow backend and stays open
            Thread.sleep(50);
        }

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

        int backend1Count = 0;
        int backend2Count = 0;

        for (CompletableFuture<HttpResponse<String>> future : futures) {
            HttpResponse<String> response = future.get();
            assertEquals(200, response.statusCode());
            if ("slow-backend-1".equals(response.body())) backend1Count++;
            if ("fast-backend-2".equals(response.body())) backend2Count++;
        }

        // With Least Connections, the slow backend should only receive 1 request (the first one).
        // While it is busy (sleeping for 1000ms), its active connection count is 1.
        // The fast backend will process all subsequent requests because its active connection count drops back to 0 instantly.
        assertTrue(backend2Count > backend1Count);
        assertEquals(1, backend1Count, "Slow backend should only handle 1 request");
        assertEquals(4, backend2Count, "Fast backend should handle the rest");
    }
}
