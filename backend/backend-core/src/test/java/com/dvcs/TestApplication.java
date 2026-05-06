package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Test-only Spring Boot application entry point for backend-core integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} within the backend-core module.
 */
@SpringBootApplication
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }
}
