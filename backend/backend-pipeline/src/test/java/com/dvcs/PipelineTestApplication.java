package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Test-only Spring Boot application entry point for backend-pipeline integration tests.
 *
 * <p>Scans all components under {@code com.dvcs} to load the full application context
 * including pipeline, auth, repository, and git modules.
 *
 * <p>{@code @EnableAsync} is required so that {@code @Async} on
 * {@link com.dvcs.pipeline.service.PipelineEngine#onPush} is honoured during tests.
 */
@SpringBootApplication
@EnableAsync
public class PipelineTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(PipelineTestApplication.class, args);
    }
}
