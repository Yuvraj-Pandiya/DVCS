package com.dvcs.common.exception;

/**
 * Thrown when a user attempts an operation they are not authorized to perform.
 *
 * <p>Maps to HTTP 403 Forbidden via the global exception handler.
 */
public class AccessDeniedException extends RuntimeException {

    public AccessDeniedException(String message) {
        super(message);
    }

    public AccessDeniedException(String message, Throwable cause) {
        super(message, cause);
    }
}
