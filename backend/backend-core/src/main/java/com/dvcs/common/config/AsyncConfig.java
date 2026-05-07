package com.dvcs.common.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Enables Spring's {@code @Async} annotation support for asynchronous method execution.
 *
 * <p>Used by {@link com.dvcs.common.audit.AuditLogService} to write audit records
 * asynchronously without blocking the request thread.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
}
