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
import org.springframework.test.context.TestPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
@TestPropertySource(properties = "nanogate.security.max-request-body-size=1KB")
class RequestSizeLimitIT {

    @LocalServerPort
    private int localPort;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    private static WireMockServer backend1;

    @BeforeAll
    static void startServer() {
        backend1 = new WireMockServer(WireMockConfiguration.options().port(8081));
        backend1.start();
    }

    @AfterAll
    static void stopServer() {
        if (backend1 != null) backend1.stop();
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testRequestBody_ExceedsLimit_Returns413() throws Exception {
        backend1.stubFor(post(urlEqualTo("/api/exact"))
                .willReturn(aResponse().withStatus(200)));

        byte[] largeBody = new byte[2048]; // > 1KB limit

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/exact"))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(largeBody))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(413, response.statusCode(), "Gateway should return 413 Payload Too Large");

        backend1.verify(0, postRequestedFor(urlEqualTo("/api/exact")));
    }
}
