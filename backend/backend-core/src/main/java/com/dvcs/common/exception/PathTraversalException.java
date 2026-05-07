package com.dvcs.common.exception;

/**
 * Thrown when a file path parameter contains path-traversal sequences
 * such as {@code ../}, {@code ..\}, or URL-encoded equivalents {@code %2e%2e}.
 *
 * <p>Maps to HTTP 400 Bad Request via the global exception handler.
 *
 * <p>Requirement 18.2: The System SHALL reject any file path parameter containing
 * path-traversal sequences with HTTP 400.
 */
public class PathTraversalException extends RuntimeException {

    public PathTraversalException(String message) {
        super(message);
    }

    public PathTraversalException(String message, Throwable cause) {
        super(message, cause);
    }
}
