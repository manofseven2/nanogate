package com.nanogate.app;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = "com.nanogate")
@ConfigurationPropertiesScan(basePackages = "com.nanogate.routing.config")
@EnableScheduling
public class NanoGateApplication {

    public static void main(String[] args) {
        SpringApplication.run(NanoGateApplication.class, args);
    }

}