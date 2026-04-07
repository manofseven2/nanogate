package com.nanogate.routing.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class HealthCheckClientConfiguration {

    @Bean
    @Qualifier("healthCheckHttpClient")
    public HttpClient healthCheckHttpClient() {
        return HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(2)) // A reasonable default connect timeout
                .build();
    }
}
