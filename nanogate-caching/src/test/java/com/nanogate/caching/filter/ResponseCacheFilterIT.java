package com.nanogate.caching.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.nanogate.caching.NanoGateCachingTestApp;
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

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateCachingTestApp.class)
@ActiveProfiles("it")
class ResponseCacheFilterIT {

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
    void testCachedResponse_HitsBackendOnce() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/cached/test"))
                .willReturn(aResponse().withStatus(200).withBody("cached-data")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/cached/test"))
                .GET()
                .build();

        // 1st request (Cache Miss)
        HttpResponse<String> response1 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response1.statusCode());
        assertEquals("cached-data", response1.body());
        assertEquals("MISS", response1.headers().firstValue("X-NanoGate-Cache").orElse(""));

        // 2nd request (Cache Hit)
        HttpResponse<String> response2 = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response2.statusCode());
        assertEquals("cached-data", response2.body());
        assertEquals("HIT", response2.headers().firstValue("X-NanoGate-Cache").orElse(""));

        // Backend should have been hit exactly ONCE
        backend1.verify(1, getRequestedFor(urlEqualTo("/api/cached/test")));
    }

    @Test
    void testVaryByHeaders_CachesSeparately() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/vary-cached/test"))
                .willReturn(aResponse().withStatus(200).withBody("vary-data")));

        HttpRequest requestEn = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/vary-cached/test"))
                .header("Accept-Language", "en-US")
                .GET()
                .build();

        HttpRequest requestFr = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/vary-cached/test"))
                .header("Accept-Language", "fr-FR")
                .GET()
                .build();

        // 1st request EN (Miss)
        HttpResponse<String> responseEn1 = httpClient.send(requestEn, HttpResponse.BodyHandlers.ofString());
        assertEquals("MISS", responseEn1.headers().firstValue("X-NanoGate-Cache").orElse(""));

        // 1st request FR (Miss)
        HttpResponse<String> responseFr1 = httpClient.send(requestFr, HttpResponse.BodyHandlers.ofString());
        assertEquals("MISS", responseFr1.headers().firstValue("X-NanoGate-Cache").orElse(""));

        // 2nd request EN (Hit)
        HttpResponse<String> responseEn2 = httpClient.send(requestEn, HttpResponse.BodyHandlers.ofString());
        assertEquals("HIT", responseEn2.headers().firstValue("X-NanoGate-Cache").orElse(""));

        // Backend should have been hit exactly TWICE (once for EN, once for FR)
        backend1.verify(2, getRequestedFor(urlEqualTo("/api/vary-cached/test")));
    }

    @Test
    void testUncachedRoute_HitsBackendEveryTime() throws Exception {
        backend1.stubFor(get(urlEqualTo("/api/uncached/test"))
                .willReturn(aResponse().withStatus(200).withBody("uncached-data")));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/uncached/test"))
                .GET()
                .build();

        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode());
            // It shouldn't have the cache header since cache is disabled entirely for this route
            assertEquals(java.util.Optional.empty(), response.headers().firstValue("X-NanoGate-Cache"));
        }

        // Backend should have been hit 3 times
        backend1.verify(3, getRequestedFor(urlEqualTo("/api/uncached/test")));
    }
}
