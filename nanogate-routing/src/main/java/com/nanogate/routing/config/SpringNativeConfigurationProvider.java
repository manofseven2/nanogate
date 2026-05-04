package com.nanogate.routing.config;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * The baseline configuration provider that reads routes seeded from Spring Boot's native property resolution
 * (application.yml, application-dev.yml, environment variables, command-line args).
 * This provider has the lowest precedence, meaning other providers (like file polling or Azure) can override it.
 */
@Component
@Order(10) // Lowest precedence
public class SpringNativeConfigurationProvider implements ConfigurationProvider {

    private final NanoGateRouteProperties baselineProperties;

    public SpringNativeConfigurationProvider(NanoGateRouteProperties baselineProperties) {
        this.baselineProperties = baselineProperties;
    }

    @Override
    public NanoGateRouteProperties fetchConfiguration() {
        return baselineProperties;
    }
}
