package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application entry point for backend-search integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} to load the full application context
 * including search, auth, repository, and git modules.
 */
@SpringBootApplication
public class SearchTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(SearchTestApplication.class, args);
    }
}
