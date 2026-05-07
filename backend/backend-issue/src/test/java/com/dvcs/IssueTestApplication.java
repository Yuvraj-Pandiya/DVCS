package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application entry point for backend-issue integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} to load the full application context
 * including issue, auth, and repository modules.
 */
@SpringBootApplication
public class IssueTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(IssueTestApplication.class, args);
    }
}
