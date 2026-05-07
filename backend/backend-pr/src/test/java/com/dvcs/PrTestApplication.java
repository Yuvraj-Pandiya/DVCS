package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application entry point for backend-pr integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} to load the full application context
 * including PR, auth, repository, git, and diff modules.
 */
@SpringBootApplication
public class PrTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrTestApplication.class, args);
    }
}
