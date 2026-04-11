package com.nanogate.routing;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * A shared Spring Boot application entry point for integration tests within the nanogate-routing module.
 * This allows @SpringBootTest to start up an isolated context focusing only on routing beans,
 * without needing the full nanogate-app module.
 */
@SpringBootApplication(scanBasePackages = "com.nanogate")
public class NanoGateRoutingTestApp {
}
