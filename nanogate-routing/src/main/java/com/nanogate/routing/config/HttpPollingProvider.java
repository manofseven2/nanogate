package com.nanogate.routing.config;

import net.logstash.logback.argument.StructuredArguments;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Order(0) // Highest precedence
public class HttpPollingProvider implements ConfigurationProvider {

    private static final Logger log = LoggerFactory.getLogger(HttpPollingProvider.class);

    private final String configUrl;
    private final ObjectMapper yamlMapper;
    private final HttpClient httpClient;
    
    private String lastFetchedBody = null;
    private NanoGateRouteProperties cachedConfig = null;

    public HttpPollingProvider(@Value("${nanogate.routing.external-config-url:}") String configUrl) {
        this.configUrl = configUrl;
        this.yamlMapper = new ObjectMapper(new YAMLFactory())
                .setPropertyNamingStrategy(com.fasterxml.jackson.databind.PropertyNamingStrategies.KEBAB_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
    }

    @Override
    public NanoGateRouteProperties fetchConfiguration() {
        if (configUrl == null || configUrl.isBlank()) {
            return null;
        }

        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(configUrl))
                    .GET()
                    .timeout(Duration.ofSeconds(2))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                String responseBody = response.body();
                
                if (responseBody != null && !responseBody.equals(lastFetchedBody)) {
                    log.info("Detected changes in external HTTP configuration: {}", configUrl);
                    NanoGateRouteProperties newConfig = yamlMapper.readValue(responseBody, NanoGateRouteProperties.class);
                    newConfig.initializeAndValidate();
                    
                    cachedConfig = newConfig;
                    lastFetchedBody = responseBody;
                }
                return cachedConfig;
            } else {
                log.warn("Failed to fetch HTTP configuration. Status code: {}", response.statusCode(),
                        StructuredArguments.kv("configUrl", configUrl),
                        StructuredArguments.kv("statusCode", response.statusCode()),
                        StructuredArguments.kv("error_type", "ConfigPollingError"));
                return cachedConfig;
            }
        } catch (Exception e) {
            log.error("Error fetching HTTP configuration from {}", configUrl, e,
                    StructuredArguments.kv("configUrl", configUrl),
                    StructuredArguments.kv("error_type", "ConfigPollingError"));
            return cachedConfig;
        }
    }
}
