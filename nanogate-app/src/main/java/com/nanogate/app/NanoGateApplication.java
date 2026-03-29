package com.nanogate.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication(scanBasePackages = "com.nanogate")
@ConfigurationPropertiesScan(basePackages = "com.nanogate.routing.config")
public class NanoGateApplication {

    static void main(String[] args) {
        SpringApplication.run(NanoGateApplication.class, args);
    }

}