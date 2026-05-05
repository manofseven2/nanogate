package com.nanogate.routing.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.File;

/**
 * A configuration provider that reads routes from an external YAML file.
 * This has higher precedence than SpringNativeConfigurationProvider.
 */
@Component
@Order(1) // Higher precedence than SpringNativeConfigurationProvider
public class YamlFilePollingProvider implements ConfigurationProvider {

    private static final Logger log = LoggerFactory.getLogger(YamlFilePollingProvider.class);

    private final String configFilePath;
    private final ObjectMapper yamlMapper;
    private long lastModifiedTime = -1;
    private NanoGateRouteProperties cachedConfig = null;

    public YamlFilePollingProvider(@Value("${nanogate.routing.external-config-file:}") String configFilePath) {
        this.configFilePath = configFilePath;
        log.info("Initialized YamlFilePollingProvider with path: '{}'", configFilePath);
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    @Override
    public NanoGateRouteProperties fetchConfiguration() {
        if (configFilePath == null || configFilePath.isBlank()) {
            return null; // Not enabled
        }

        File file = new File(configFilePath);
        if (!file.exists() || !file.canRead()) {
            log.trace("External configuration file {} not found or not readable.", configFilePath);
            return null;
        }

        long currentModifiedTime = file.lastModified();
        if (currentModifiedTime > lastModifiedTime) {
            log.info("Detected changes in external configuration file: {}", configFilePath);
            try {
                // To support the structure 'nanogate: routing: routes: ...' we could wrap it
                // For simplicity, we assume the YAML file directly maps to NanoGateRouteProperties
                // i.e., it contains `routes:` and `backend-sets:` at the root level.
                NanoGateRouteProperties newConfig = yamlMapper.readValue(file, NanoGateRouteProperties.class);
                newConfig.initializeAndValidate();
                cachedConfig = newConfig;
                lastModifiedTime = currentModifiedTime;
                return cachedConfig;
            } catch (Exception e) {
                log.error("Failed to parse external YAML configuration from {}", configFilePath, e);
                // Return cached config if parsing fails, so we don't break the gateway
                return cachedConfig; 
            }
        }
        return cachedConfig;
    }
}
