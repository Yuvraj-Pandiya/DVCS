package com.dvcs.auth.service;

import com.dvcs.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JJWT-backed implementation of {@link JwtUtil}.
 *
 * <p>Access tokens use HS256 with a 15-minute expiry and carry the following claims:
 * <ul>
 *   <li>{@code sub} — user ID (as a string)</li>
 *   <li>{@code username} — the user's login name</li>
 *   <li>{@code roles} — list of role strings, e.g. {@code ["USER"]}</li>
 * </ul>
 *
 * <p>The signing key is derived from the {@code jwt.secret} property (or the
 * {@code JWT_SECRET} environment variable). The secret must be at least 32 characters
 * long to satisfy the HS256 minimum key length of 256 bits.
 */
@Component
public class JwtUtilImpl implements JwtUtil {

    private static final Logger log = LoggerFactory.getLogger(JwtUtilImpl.class);

    /** Claim name for the username. */
    private static final String CLAIM_USERNAME = "username";

    /** Claim name for the roles list. */
    private static final String CLAIM_ROLES = "roles";

    /** Default role assigned to every user. */
    private static final List<String> DEFAULT_ROLES = List.of("USER");

    private final SecretKey signingKey;
    private final long accessTokenExpiryMs;

    /**
     * Constructs the implementation, deriving the signing key and expiry from configuration.
     *
     * @param secret            the JWT secret (min 32 chars for HS256 256-bit key)
     * @param expirySeconds     access-token lifetime in seconds (default 900 = 15 min)
     */
    public JwtUtilImpl(
            @Value("${jwt.secret}") String secret,
            @Value("${jwt.expiry-seconds:900}") long expirySeconds) {

        // Pad or truncate to exactly 32 bytes so Keys.hmacShaKeyFor always succeeds.
        // A production secret should already be ≥ 32 chars; this guard prevents startup
        // failures in test environments that use short secrets.
        byte[] keyBytes = ensureKeyLength(secret.getBytes(StandardCharsets.UTF_8), 32);
        this.signingKey = Keys.hmacShaKeyFor(keyBytes);
        this.accessTokenExpiryMs = expirySeconds * 1_000L;
    }

    // -------------------------------------------------------------------------
    // JwtUtil implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Generates a signed HS256 JWT with:
     * <ul>
     *   <li>{@code sub} = user ID (string)</li>
     *   <li>{@code username} = user's login name</li>
     *   <li>{@code roles} = {@code ["USER"]}</li>
     *   <li>{@code iat} / {@code exp} = now / now + 15 min</li>
     * </ul>
     */
    @Override
    public String generateAccessToken(User user) {
        long nowMs = System.currentTimeMillis();
        return Jwts.builder()
                .subject(String.valueOf(user.getId()))
                .claim(CLAIM_USERNAME, user.getUsername())
                .claim(CLAIM_ROLES, DEFAULT_ROLES)
                .issuedAt(new Date(nowMs))
                .expiration(new Date(nowMs + accessTokenExpiryMs))
                .signWith(signingKey)
                .compact();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Returns {@code true} only when the token parses successfully and has not expired.
     * All exceptions are caught and logged at DEBUG level; the method never throws.
     */
    @Override
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException e) {
            log.debug("JWT validation failed: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.debug("JWT token is null or empty: {}", e.getMessage());
        }
        return false;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Extracts the {@code sub} claim and converts it to a {@link Long}.
     *
     * @throws JwtException             if the token is invalid or expired
     * @throws NumberFormatException    if the subject is not a valid long
     */
    @Override
    public Long extractUserId(String token) {
        return Long.parseLong(parseClaims(token).getSubject());
    }

    /**
     * {@inheritDoc}
     *
     * @throws JwtException if the token is invalid or expired
     */
    @Override
    public String extractUsername(String token) {
        return parseClaims(token).get(CLAIM_USERNAME, String.class);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses and validates the JWT, returning its {@link Claims}.
     *
     * @param token the compact JWT string
     * @return the verified claims
     * @throws JwtException             on any parse or validation failure
     * @throws IllegalArgumentException if {@code token} is null or blank
     */
    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * Ensures the key byte array is exactly {@code requiredLength} bytes.
     * If shorter, the array is right-padded with zeros.
     * If longer, it is used as-is (HMAC keys may be any length ≥ minimum).
     *
     * @param bytes          the raw key bytes
     * @param requiredLength minimum required length in bytes
     * @return a byte array of at least {@code requiredLength} bytes
     */
    private static byte[] ensureKeyLength(byte[] bytes, int requiredLength) {
        if (bytes.length >= requiredLength) {
            return bytes;
        }
        byte[] padded = new byte[requiredLength];
        System.arraycopy(bytes, 0, padded, 0, bytes.length);
        return padded;
    }
}
