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
class RouteResolutionFilterIT {

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
    void testUrlAndHeaderRewriteRoute() throws Exception {
        backend1.stubFor(get(urlEqualTo("/user/fetch?id=123"))
                .withHeader("X-Downstream-Trace-Id", equalTo("nanogate-abc-123"))
                .willReturn(aResponse().withStatus(200).withBody("{\"user\":\"rewritten\"}")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/users/123"))
                .header("X-Request-Id", "abc-123")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertEquals("{\"user\":\"rewritten\"}", response.body());

        backend1.verify(1, getRequestedFor(urlEqualTo("/user/fetch?id=123"))
                .withHeader("X-Downstream-Trace-Id", equalTo("nanogate-abc-123")));
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
    
    @Test
    void testRateLimiting() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/ratelimit/test"))
                .willReturn(aResponse().withStatus(200).withBody("ok")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/ratelimit/test"))
                .header("X-Forwarded-For", "192.168.1.50")
                .GET()
                .build();

        // 1st request should pass
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response1.statusCode());

        // 2nd request should pass
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response2.statusCode());

        // 3rd request should hit the rate limit (2 req/sec) and fail with 429
        HttpResponse<String> response3 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(429, response3.statusCode());
    }

    // --- All other existing tests remain here ---
}
