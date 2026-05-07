package com.dvcs.common.exception;

/**
 * Thrown when a client exceeds the applicable rate limit.
 *
 * <p>Maps to HTTP 429 Too Many Requests via the global exception handler.
 * The {@link #getRetryAfterSeconds()} value should be included in the
 * {@code Retry-After} response header.
 */
public class RateLimitExceededException extends RuntimeException {

    private final long retryAfterSeconds;

    public RateLimitExceededException(String message, long retryAfterSeconds) {
        super(message);
        this.retryAfterSeconds = retryAfterSeconds;
    }

    /**
     * Returns the number of seconds the client should wait before retrying.
     *
     * @return seconds until the rate-limit window resets
     */
    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
