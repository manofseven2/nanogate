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

import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Random;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("it")
@TestPropertySource(properties = "nanogate.security.max-request-body-size=25MB")
class LargePayloadIT {

    @LocalServerPort
    private int localPort;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private static WireMockServer largePayloadBackend;

    @BeforeAll
    static void startServer() {
        largePayloadBackend = new WireMockServer(WireMockConfiguration.options().port(8091));
        largePayloadBackend.start();
        largePayloadBackend.stubFor(get(urlEqualTo("/health")).willReturn(aResponse().withStatus(200)));
    }

    @AfterAll
    static void stopServer() {
        if (largePayloadBackend != null) largePayloadBackend.stop();
    }

    private String getBaseUrl() {
        return "http://localhost:" + localPort;
    }

    @Test
    void testPostLargeBody_ReceivesLargeBody() throws Exception {
        int bodySize = 20 * 1024 * 1024;
        byte[] largeBody = new byte[bodySize];
        new Random().nextBytes(largeBody);

        // Stub the backend to expect the exact body and echo it back
        largePayloadBackend.stubFor(post(urlEqualTo("/api/large-post/echo"))
                .withRequestBody(binaryEqualTo(largeBody))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", "application/octet-stream")
                        .withBody(largeBody)));

        // Build and send the POST request
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(getBaseUrl() + "/api/large-post/echo"))
                .timeout(Duration.ofSeconds(60))
                .header("Content-Type", "application/octet-stream")
                .POST(HttpRequest.BodyPublishers.ofByteArray(largeBody))
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
        assertEquals(bodySize, bytesRead, "The full large response body should have been streamed");

        // Verify that the backend server received the request with the correct body
        largePayloadBackend.verify(1, postRequestedFor(urlEqualTo("/api/large-post/echo"))
                .withRequestBody(binaryEqualTo(largeBody)));
    }
}
