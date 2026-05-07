package com.dvcs;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

/**
 * Test-only Spring Boot application entry point for backend-notification integration tests.
 *
 * <p>Scans components under {@code com.dvcs} to load the application context
 * including notification, auth, and repository modules.
 *
 * <p>Note: BlobController and TreeController are excluded because they depend on
 * GitObjectReaderService from backend-git, which is not a dependency of this module.
 */
@SpringBootApplication
@ComponentScan(
        basePackages = "com.dvcs",
        excludeFilters = @ComponentScan.Filter(
                type = FilterType.REGEX,
                pattern = {
                        "com\\.dvcs\\.repository\\.controller\\.BlobController",
                        "com\\.dvcs\\.repository\\.controller\\.TreeController",
                        "com\\.dvcs\\.repository\\.controller\\.CommitController",
                        "com\\.dvcs\\.repository\\.service\\.GitObjectReaderService"
                }
        )
)
public class NotificationTestApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationTestApplication.class, args);
    }
}
