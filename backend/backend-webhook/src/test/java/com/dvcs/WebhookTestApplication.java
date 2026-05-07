package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Test-only Spring Boot application entry point for backend-webhook integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} to load the full application context
 * including webhook, auth, and repository modules.
 */
@SpringBootApplication
@EnableAsync
public class WebhookTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(WebhookTestApplication.class, args);
    }
}
