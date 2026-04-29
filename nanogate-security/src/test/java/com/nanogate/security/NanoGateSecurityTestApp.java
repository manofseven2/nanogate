package com.nanogate.security;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@SpringBootApplication(scanBasePackages = "com.nanogate.security")
public class NanoGateSecurityTestApp {
    public static void main(String[] args) {
        SpringApplication.run(NanoGateSecurityTestApp.class, args);
    }

    @RestController
    static class TestController {
        @GetMapping("/test")
        public String test() {
            return "OK";
        }
    }
}
