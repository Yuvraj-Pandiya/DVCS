package com.dvcs.auth.service;

import com.dvcs.auth.domain.User;

/**
 * Contract for JWT operations used by the authentication layer.
 *
 * <p>The concrete implementation ({@link JwtUtilImpl}) is provided in task 3.4.
 * This interface allows {@link AuthService} to depend on an abstraction and be
 * tested independently of the JWT library.
 */
public interface JwtUtil {

    /**
     * Generates a short-lived (15-minute) JWT access token for the given user.
     *
     * @param user the authenticated user
     * @return a signed JWT string
     */
    String generateAccessToken(User user);

    /**
     * Validates a JWT token's signature and expiry.
     *
     * @param token the JWT string to validate
     * @return {@code true} if the token is valid and not expired, {@code false} otherwise
     */
    boolean validateToken(String token);

    /**
     * Extracts the user ID ({@code sub} claim) from a JWT token.
     *
     * @param token a valid JWT string
     * @return the user ID encoded in the token
     */
    Long extractUserId(String token);

    /**
     * Extracts the username claim from a JWT token.
     *
     * @param token a valid JWT string
     * @return the username encoded in the token
     */
    String extractUsername(String token);
}
