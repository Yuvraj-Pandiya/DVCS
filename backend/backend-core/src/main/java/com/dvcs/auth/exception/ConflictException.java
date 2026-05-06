package com.dvcs.auth.exception;

/**
 * Thrown when a resource creation request conflicts with an existing resource,
 * e.g. duplicate username or email during registration.
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
