package com.nanogate.routing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Periodically checks the ConfigurationProviders to see if a new configuration is available.
 * If a new valid configuration is found, it updates the RouteRegistry and publishes an event.
 */
@Service
public class ConfigurationRefreshService {

    private static final Logger log = LoggerFactory.getLogger(ConfigurationRefreshService.class);

    private final RouteRegistry routeRegistry;
    private final List<ConfigurationProvider> providers;
    private final ApplicationEventPublisher eventPublisher;

    public ConfigurationRefreshService(RouteRegistry routeRegistry, 
                                       List<ConfigurationProvider> providers,
                                       ApplicationEventPublisher eventPublisher) {
        this.routeRegistry = routeRegistry;
        this.providers = providers;
        this.eventPublisher = eventPublisher;
    }

    // Runs every 5 seconds by default
    @Scheduled(fixedDelayString = "${nanogate.routing.refresh-interval:5000}")
    public void checkForUpdates() {
        // Providers are injected in order of their @Order annotation (10, 1, etc.)
        // We iterate through them, but since we want the HIGHEST precedence to win,
        // and Spring orders from lowest to highest value (1 then 10), we should traverse in reverse
        // OR simply find the first provider that returns a non-null config when traversing backwards.
        // Wait, @Order(1) is highest precedence in Spring.
        // SpringNative is @Order(1). YamlFile is @Order(10). 
        // Wait, lower value means higher precedence.
        // If we want YamlFile to override SpringNative, YamlFile should be @Order(1) and SpringNative @Order(10).
        // Let's just iterate through them and if a higher precedence one (lower @Order value)
        // returns a config, we use it and stop.

        NanoGateRouteProperties newConfig = null;

        for (ConfigurationProvider provider : providers) {
            NanoGateRouteProperties config = provider.fetchConfiguration();
            if (config != null) {
                newConfig = config;
                break; // Found the highest precedence config
            }
        }

        if (newConfig != null && hasConfigurationChanged(newConfig)) {
            log.info("Detected routing configuration change. Hot-swapping routes...");
            routeRegistry.update(newConfig);
            eventPublisher.publishEvent(new ConfigurationRefreshedEvent(this, newConfig));
        }
    }

    private boolean hasConfigurationChanged(NanoGateRouteProperties newConfig) {
        NanoGateRouteProperties currentConfig = routeRegistry.get();
        // Since we parse a new NanoGateRouteProperties object from YAML each time it changes,
        // we can simply check if the object references are different.
        // If it's the exact same object from SpringNative, it will be ==.
        return currentConfig != newConfig;
    }
}
