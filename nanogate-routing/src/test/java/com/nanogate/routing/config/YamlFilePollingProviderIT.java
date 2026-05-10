package com.nanogate.routing.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class YamlFilePollingProviderIT {

    @TempDir
    Path tempDir;

    @Test
    void shouldReadConfigurationFromFile() {
        // Use the file we created in src/test/resources
        String configPath = "src/test/resources/external-routes.yml";
        YamlFilePollingProvider provider = new YamlFilePollingProvider(configPath);
        NanoGateRouteProperties config = provider.fetchConfiguration();

        assertThat(config).isNotNull();
        assertThat(config.getRoutes()).hasSize(1);
        assertThat(config.getRoutes().get(0).getId()).isEqualTo("external-route-1");
        assertThat(config.getBackendSets()).hasSize(1);
    }

    @Test
    void shouldDetectFileChanges() throws IOException, InterruptedException {
        Path configFile = tempDir.resolve("dynamic-routes.yml");
        // Start by copying the template from resources
        Files.copy(Path.of("src/test/resources/external-routes.yml"), configFile);

        YamlFilePollingProvider provider = new YamlFilePollingProvider(configFile.toString());
        NanoGateRouteProperties initialConfig = provider.fetchConfiguration();
        assertThat(initialConfig.getRoutes()).hasSize(1);

        // Sleep to ensure the lastModified timestamp will be different
        Thread.sleep(1001);

        String updatedYaml = """
                routes:
                  - id: external-route-1
                    path: /external/v1/**
                    backendSet: test-backend-1
                  - id: external-route-2
                    path: /external/v2/**
                    backendSet: test-backend-1
                backendSets:
                  - name: test-backend-1
                    servers:
                      - http://localhost:8081
                """;
        Files.writeString(configFile, updatedYaml);

        NanoGateRouteProperties updatedConfig = provider.fetchConfiguration();
        assertThat(updatedConfig.getRoutes()).hasSize(2);
        assertThat(updatedConfig.getRoutes().get(1).getId()).isEqualTo("external-route-2");
    }

    @Test
    void shouldReturnNullWhenFileNotConfigured() {
        YamlFilePollingProvider provider = new YamlFilePollingProvider("");
        assertThat(provider.fetchConfiguration()).isNull();
    }

    @Test
    void shouldReturnNullWhenFileNotFound() {
        YamlFilePollingProvider provider = new YamlFilePollingProvider("non-existent.yml");
        assertThat(provider.fetchConfiguration()).isNull();
    }
}
