package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * DVCS Platform — Spring Boot application entry point.
 *
 * <p>This class bootstraps the entire modular monolith. All modules
 * (core, git, diff, pr, issue, webhook, pipeline, notification, search)
 * are loaded as Spring components via classpath scanning under {@code com.dvcs}.
 */
@SpringBootApplication
public class DvcsApplication {

    public static void main(String[] args) {
        SpringApplication.run(DvcsApplication.class, args);
    }
}
