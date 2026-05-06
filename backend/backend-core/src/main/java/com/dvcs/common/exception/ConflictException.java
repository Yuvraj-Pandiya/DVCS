package com.dvcs.common.exception;

/**
 * Thrown when a resource creation request conflicts with an existing resource.
 *
 * <p>Maps to HTTP 409 Conflict via the global exception handler.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }

    public ConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
