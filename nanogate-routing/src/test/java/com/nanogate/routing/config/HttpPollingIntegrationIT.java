package com.nanogate.routing.config;

import com.github.tomakehurst.wiremock.junit5.WireMockRuntimeInfo;
import com.github.tomakehurst.wiremock.junit5.WireMockTest;
import com.nanogate.routing.NanoGateRoutingTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NanoGateRoutingTestApp.class)
@WireMockTest(httpPort = 8090)
class HttpPollingIntegrationIT {

    @Autowired
    private HttpPollingProvider pollingProvider;

    @Autowired
    private ConfigurationRefreshService refreshService;
    
    @Autowired
    private RouteRegistry routeRegistry;

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("nanogate.routing.external-config-url", () -> "http://localhost:8090/config.yml");
    }

    @Test
    void shouldDynamicallyUpdateConfigurationWhenRemoteServerChanges(WireMockRuntimeInfo wmRuntimeInfo) {
        // V1 Config
        String configV1 = 
                "backend-sets:\n" +
                "  - name: mock-backend\n" +
                "    servers:\n" +
                "      - http://localhost:8080\n" +
                "routes:\n" +
                "  - id: v1-route\n" +
                "    path: /api/v1/**\n" +
                "    backend-set: mock-backend\n";
                
        stubFor(get("/config.yml").willReturn(ok(configV1)));

        // 1. Initial Load
        refreshService.checkForUpdates();
        
        assertThat(routeRegistry.get().getRoutes().stream().anyMatch(r -> r.getId().equals("v1-route"))).isTrue();
        assertThat(routeRegistry.get().getRoutes().stream().anyMatch(r -> r.getId().equals("v2-route"))).isFalse();

        // V2 Config
        String configV2 = 
                "backend-sets:\n" +
                "  - name: mock-backend\n" +
                "    servers:\n" +
                "      - http://localhost:8080\n" +
                "routes:\n" +
                "  - id: v1-route\n" +
                "    path: /api/v1/**\n" +
                "    backend-set: mock-backend\n" +
                "  - id: v2-route\n" +
                "    path: /api/v2/**\n" +
                "    backend-set: mock-backend\n";
                
        // Update WireMock stub
        stubFor(get("/config.yml").willReturn(ok(configV2)));
        
        // 2. Hot Reload
        refreshService.checkForUpdates();
        
        // Verify RouteRegistry now contains the new route
        assertThat(routeRegistry.get().getRoutes().stream().anyMatch(r -> r.getId().equals("v1-route"))).isTrue();
        assertThat(routeRegistry.get().getRoutes().stream().anyMatch(r -> r.getId().equals("v2-route"))).isTrue();
    }
}
