package com.dvcs.common.error;

import java.time.Instant;
import java.util.Map;

/**
 * Standard error response envelope returned for all 4xx and 5xx responses.
 *
 * <p>Format: {@code { error, message, details, timestamp }}
 */
public record ErrorEnvelope(
        String error,
        String message,
        Map<String, Object> details,
        Instant timestamp
) {
    public static ErrorEnvelope of(String error, String message) {
        return new ErrorEnvelope(error, message, Map.of(), Instant.now());
    }

    public static ErrorEnvelope of(String error, String message, Map<String, Object> details) {
        return new ErrorEnvelope(error, message, details, Instant.now());
    }
}
