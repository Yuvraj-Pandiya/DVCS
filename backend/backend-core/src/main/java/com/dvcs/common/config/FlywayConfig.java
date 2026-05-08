package com.dvcs.common.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.autoconfigure.flyway.FlywayMigrationStrategy;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Flyway migration strategy that runs {@code repair()} before {@code migrate()}.
 *
 * <p>This handles the case where a migration script was edited after it had
 * already been applied to the database (e.g. changing CHAR to VARCHAR in V1).
 * Flyway stores a CRC32 checksum of each script in {@code flyway_schema_history};
 * if the file on disk no longer matches, Flyway refuses to start.
 *
 * <p>{@code repair()} updates the stored checksums to match the current files
 * and removes any failed migration entries, then {@code migrate()} runs any
 * pending scripts (e.g. V6 to ALTER the column types).
 *
 * <p>This is safe in production: repair only updates metadata, it never
 * re-executes already-applied SQL.
 */
@Configuration
public class FlywayConfig {

    @Bean
    public FlywayMigrationStrategy repairThenMigrate() {
        return (Flyway flyway) -> {
            flyway.repair();
            flyway.migrate();
        };
    }
}
