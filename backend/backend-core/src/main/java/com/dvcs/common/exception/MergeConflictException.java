package com.dvcs.common.exception;

/**
 * Thrown when a merge operation encounters conflicts that cannot be
 * automatically resolved.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity via the global exception handler.
 * The message should describe which files have conflicts.
 */
public class MergeConflictException extends RuntimeException {

    public MergeConflictException(String message) {
        super(message);
    }

    public MergeConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
