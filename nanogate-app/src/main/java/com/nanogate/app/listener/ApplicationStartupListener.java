package com.nanogate.app.listener;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;

/**
 * A component that prints a startup message with the application's address, port, and active profiles
 * once the application is fully running.
 */
@Component
public class ApplicationStartupListener implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(ApplicationStartupListener.class);

    private final Environment environment;

    public ApplicationStartupListener(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void run(String... args) {
        try {
            String hostAddress = InetAddress.getLocalHost().getHostAddress();
            // Get the port from the environment properties, which is more reliable
            String port = environment.getProperty("local.server.port");
            String[] activeProfiles = environment.getActiveProfiles();
            String appName = environment.getProperty("spring.application.name", "NanoGate");

            log.info("""

                --------------------------------------------------------------------------------
                \tApplication '{}' is running! Access URLs:
                \tLocal: \t\thttp://localhost:{}
                \tExternal: \thttp://{}:{}
                \tActive profiles: {}
                --------------------------------------------------------------------------------""",
                    appName,
                    port,
                    hostAddress,
                    port,
                    (activeProfiles.length == 0) ? "default" : String.join(", ", activeProfiles)
            );
        } catch (UnknownHostException e) {
            log.warn("Could not determine local host address", e);
        }
    }
}