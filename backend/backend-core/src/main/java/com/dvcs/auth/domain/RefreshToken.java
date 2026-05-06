package com.dvcs.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity representing a persisted refresh token, mapped to the {@code refresh_tokens} table.
 *
 * <p>Only the SHA-256 hash of the raw token is stored; the raw value is never persisted.
 * A token is considered valid when {@code revoked == false} and {@code expiresAt} is in the future.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The ID of the user this token belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * SHA-256 hex digest of the raw refresh token UUID.
     * Stored instead of the raw value so that a database breach cannot be used to impersonate users.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    /** Absolute expiry time. Tokens past this instant are invalid regardless of the revoked flag. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /**
     * Whether this token has been explicitly revoked (e.g. used in a rotation or logged out).
     * Once {@code true}, the token MUST NOT be accepted even if it has not yet expired.
     */
    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;
}
