package com.nanogate.routing.filter;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
class CorsFilterIT {

    @LocalServerPort
    private int port;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))
            .build();

    private static WireMockServer wireMockServer1;

    @BeforeAll
    static void setUpClass() {
        wireMockServer1 = new WireMockServer(WireMockConfiguration.wireMockConfig().port(8081));
        wireMockServer1.start();
        
        // Mock backend
        wireMockServer1.stubFor(WireMock.get(WireMock.urlMatching("/api/cors/.*"))
                .willReturn(WireMock.aResponse().withStatus(200).withBody("CORS Backend Success")));
    }

    @AfterAll
    static void tearDownClass() {
        if (wireMockServer1 != null) {
            wireMockServer1.stop();
        }
    }

    @Test
    void testCorsPreflightAllowed() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/cors/test"))
                .header("Origin", "http://allowed-origin.com")
                .header("Access-Control-Request-Method", "GET")
                .header("Access-Control-Request-Headers", "X-Custom-Header")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        Optional<String> allowOrigin = response.headers().firstValue("Access-Control-Allow-Origin");
        assertThat(allowOrigin).isPresent().contains("http://allowed-origin.com");
        
        // Preflight shouldn't hit backend, so wiremock doesn't get an OPTIONS request
        wireMockServer1.verify(0, WireMock.optionsRequestedFor(WireMock.urlMatching("/api/cors/.*")));
    }

    @Test
    void testCorsPreflightRejectedOrigin() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/cors/test"))
                .header("Origin", "http://bad-origin.com")
                .header("Access-Control-Request-Method", "GET")
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // Spring CorsFilter returns 403 Forbidden for invalid CORS origin
        assertThat(response.statusCode()).isEqualTo(403);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
    }

    @Test
    void testCorsActualRequest() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/cors/test"))
                .header("Origin", "http://allowed-origin.com")
                .GET()
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("CORS Backend Success");
        
        Optional<String> allowOrigin = response.headers().firstValue("Access-Control-Allow-Origin");
        assertThat(allowOrigin).isPresent().contains("http://allowed-origin.com");
    }
}
