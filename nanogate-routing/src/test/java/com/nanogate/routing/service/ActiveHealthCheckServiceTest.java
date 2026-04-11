package com.nanogate.routing.service;

import com.nanogate.routing.config.NanoGateRouteProperties;
import com.nanogate.routing.model.HealthCheckProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActiveHealthCheckServiceTest {

    @Mock
    private NanoGateRouteProperties properties;

    @Mock
    private HttpClient healthCheckClient;

    @Mock
    private HttpResponse<String> mockHttpResponse;

    private ActiveHealthCheckService healthCheckService;

    private URI server1;
    private HealthCheckProperties healthCheckProps;

    @BeforeEach
    void setUp() throws Exception {
        server1 = new URI("http://localhost:8081");
        healthCheckProps = new HealthCheckProperties("/health", Duration.ofSeconds(10), Duration.ofSeconds(2));

        healthCheckService = new ActiveHealthCheckService(properties, healthCheckClient);
    }

    @Test
    void testIsHealthy_UnmonitoredServer_ReturnsTrue() throws URISyntaxException {
        assertTrue(healthCheckService.isHealthy(new URI("http://unmonitored:8080")));
    }

    @Test
    void testCheckServerHealth_HealthyServer_MarksAsHealthy() {
        // Given
        when(mockHttpResponse.statusCode()).thenReturn(200);
        when(healthCheckClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));

        // When
        healthCheckService.checkServerHealth(server1, healthCheckProps).join();

        // Then
        assertTrue(healthCheckService.isHealthy(server1));
    }

    @Test
    void testCheckServerHealth_UnhealthyServer_MarksAsUnhealthy() {
        // Given
        when(mockHttpResponse.statusCode()).thenReturn(503);
        when(healthCheckClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(CompletableFuture.completedFuture(mockHttpResponse));

        // When
        healthCheckService.checkServerHealth(server1, healthCheckProps).join();

        // Then
        assertFalse(healthCheckService.isHealthy(server1));
    }

    @Test
    void testCheckServerHealth_RequestFails_MarksAsUnhealthy() {
        // Given
        CompletableFuture<HttpResponse<String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new IOException("Connection refused"));
        when(healthCheckClient.sendAsync(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(failedFuture);

        // When
        healthCheckService.checkServerHealth(server1, healthCheckProps).join();

        // Then
        assertFalse(healthCheckService.isHealthy(server1));
    }

    @Test
    void testMarkAsUnhealthy_ChangesStateFromUpToDown() {
        // Initially, the server is healthy by default
        assertTrue(healthCheckService.isHealthy(server1));

        // When we manually mark it as unhealthy
        healthCheckService.markAsUnhealthy(server1);

        // Then, it should be considered unhealthy
        assertFalse(healthCheckService.isHealthy(server1));
    }
}
