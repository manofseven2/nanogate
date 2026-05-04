package com.nanogate.caching;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = {"com.nanogate.caching", "com.nanogate.routing", "com.nanogate.resilience", "com.nanogate.security"})
public class NanoGateCachingTestApp {
    public static void main(String[] args) {
        SpringApplication.run(NanoGateCachingTestApp.class, args);
    }
}
