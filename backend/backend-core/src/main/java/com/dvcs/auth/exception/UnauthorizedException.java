package com.dvcs.auth.exception;

/**
 * Thrown when authentication fails, e.g. invalid credentials or an expired/revoked token.
 *
 * <p>Maps to HTTP 401 Unauthorized via the global exception handler.
 * The message MUST NOT reveal which specific field (username vs. password) was incorrect.
 */
public class UnauthorizedException extends RuntimeException {

    public UnauthorizedException(String message) {
        super(message);
    }

    public UnauthorizedException(String message, Throwable cause) {
        super(message, cause);
    }
}
