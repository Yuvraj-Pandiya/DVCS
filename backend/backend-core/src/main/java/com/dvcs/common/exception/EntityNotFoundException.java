package com.dvcs.common.exception;

/**
 * Thrown when a requested resource does not exist or is not accessible to the caller.
 *
 * <p>Maps to HTTP 404 Not Found via the global exception handler.
 * Per Req 3.4, private repositories return 404 (not 403) to avoid disclosing their existence.
 */
public class EntityNotFoundException extends RuntimeException {

    public EntityNotFoundException(String message) {
        super(message);
    }

    public EntityNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
