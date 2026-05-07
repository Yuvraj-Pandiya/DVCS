package com.dvcs.common.exception;

/**
 * Thrown when a request violates a business rule that cannot be expressed
 * as a simple validation constraint.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity via the global exception handler.
 * Examples: opening a PR with identical head and base branches, attempting
 * to merge a PR that is not mergeable.
 */
public class InvalidRequestException extends RuntimeException {

    public InvalidRequestException(String message) {
        super(message);
    }

    public InvalidRequestException(String message, Throwable cause) {
        super(message, cause);
    }
}
