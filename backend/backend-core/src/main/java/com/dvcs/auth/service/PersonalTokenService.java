package com.dvcs.auth.service;

import com.dvcs.auth.domain.PersonalToken;
import com.dvcs.auth.dto.PersonalTokenInfo;
import com.dvcs.auth.dto.PersonalTokenResponse;
import com.dvcs.auth.repository.PersonalTokenRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.HexFormat;
import java.util.List;

/**
 * Service for managing personal access tokens (PATs).
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Generate a cryptographically secure random token and return it exactly once.</li>
 *   <li>Store only the SHA-256 hash of the raw token — the raw value is never persisted.</li>
 *   <li>List token metadata (no hash, no raw value) for a given user.</li>
 *   <li>Revoke (delete) a token, enforcing ownership.</li>
 * </ul>
 *
 * <p><strong>Security invariant:</strong> the raw token value MUST NOT be logged or stored
 * anywhere after it is returned from {@link #createToken}. Only the SHA-256 hex digest is
 * persisted in the database.
 */
@Service
@Transactional
public class PersonalTokenService {

    /** Number of random bytes used to generate a token (produces a 64-char hex string). */
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final PersonalTokenRepository personalTokenRepository;
    private final SecureRandom secureRandom;

    public PersonalTokenService(PersonalTokenRepository personalTokenRepository) {
        this.personalTokenRepository = personalTokenRepository;
        this.secureRandom = new SecureRandom();
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Creates a new personal access token for the given user.
     *
     * <p>A cryptographically secure 32-byte random value is generated and hex-encoded into
     * a 64-character string. Its SHA-256 digest is stored in the database. The raw token
     * value is included in the returned {@link PersonalTokenResponse} and is <em>never</em>
     * returned or logged again.
     *
     * @param userId    the ID of the user creating the token
     * @param name      a human-readable label (e.g. "CI pipeline")
     * @param scopes    the list of scopes this token is permitted to use
     * @param expiresAt optional absolute expiry time; {@code null} means the token never expires
     * @return a {@link PersonalTokenResponse} containing the raw token (one-time) and metadata
     */
    public PersonalTokenResponse createToken(Long userId,
                                             String name,
                                             List<String> scopes,
                                             OffsetDateTime expiresAt) {
        // Generate a cryptographically secure random token (32 bytes → 64-char hex string).
        byte[] tokenBytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = HexFormat.of().formatHex(tokenBytes);

        // Compute SHA-256 hash of the raw token. Only the hash is persisted.
        String tokenHash = sha256Hex(rawToken);

        PersonalToken token = PersonalToken.builder()
                .userId(userId)
                .name(name)
                .tokenHash(tokenHash)
                .scopes(scopes)
                .expiresAt(expiresAt != null ? expiresAt.toInstant() : null)
                .build();

        PersonalToken saved = personalTokenRepository.save(token);

        // Return the raw token exactly once. It is not stored and will never be retrievable again.
        return new PersonalTokenResponse(
                saved.getId(),
                saved.getName(),
                saved.getScopes(),
                saved.getExpiresAt(),
                saved.getCreatedAt(),
                rawToken   // one-time raw value — caller must store this securely
        );
    }

    /**
     * Returns metadata for all personal access tokens belonging to the given user.
     *
     * <p>The response contains no raw token values and no token hashes.
     *
     * @param userId the ID of the user whose tokens to list
     * @return list of {@link PersonalTokenInfo} records, possibly empty
     */
    @Transactional(readOnly = true)
    public List<PersonalTokenInfo> listTokens(Long userId) {
        return personalTokenRepository.findByUserId(userId).stream()
                .map(t -> new PersonalTokenInfo(
                        t.getId(),
                        t.getName(),
                        t.getScopes(),
                        t.getExpiresAt(),
                        t.getCreatedAt()))
                .toList();
    }

    /**
     * Revokes (deletes) a personal access token by ID.
     *
     * <p>Ownership is enforced: if the token exists but belongs to a different user,
     * a {@code 403 Forbidden} response status exception is thrown.
     *
     * @param tokenId the ID of the token to revoke
     * @param userId  the ID of the user requesting revocation
     * @throws EntityNotFoundException   if no token with {@code tokenId} exists
     * @throws ResponseStatusException   with HTTP 403 if the token belongs to a different user
     */
    public void revokeToken(Long tokenId, Long userId) {
        PersonalToken token = personalTokenRepository.findById(tokenId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Personal access token not found with id: " + tokenId));

        if (!token.getUserId().equals(userId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "Access denied: token " + tokenId + " does not belong to user " + userId);
        }

        personalTokenRepository.delete(token);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the SHA-256 hex digest of the given string (UTF-8 encoded).
     *
     * @param input the string to hash
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     */
    static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM (JCA spec)
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
