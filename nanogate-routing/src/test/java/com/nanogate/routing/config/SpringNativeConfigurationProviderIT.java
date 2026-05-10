package com.nanogate.routing.config;

import com.nanogate.routing.NanoGateRoutingTestApp;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(classes = NanoGateRoutingTestApp.class)
@ActiveProfiles("native-it")
class SpringNativeConfigurationProviderIT {

    @Autowired
    private SpringNativeConfigurationProvider provider;

    @Test
    void shouldFetchConfigurationFromSpringEnvironment() {
        NanoGateRouteProperties config = provider.fetchConfiguration();

        assertThat(config).isNotNull();
        assertThat(config.isEnabled()).isTrue();
        assertThat(config.getRoutes()).hasSize(1);
        assertThat(config.getRoutes().get(0).getId()).isEqualTo("native-route");
        assertThat(config.getRoutes().get(0).getPath()).isEqualTo("/native/**");
        assertThat(config.getBackendSet("backend-1")).isNotNull();
    }
}
