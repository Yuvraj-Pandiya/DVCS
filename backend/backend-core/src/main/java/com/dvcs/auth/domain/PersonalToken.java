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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * JPA entity representing a personal access token, mapped to the {@code personal_tokens} table.
 *
 * <p>Only the SHA-256 hash of the raw token is stored; the raw value is returned to the caller
 * once at creation time and never persisted. A token is considered valid when {@code expiresAt}
 * is {@code null} (no expiry) or is in the future.
 *
 * <p>Each token carries a list of scopes (e.g. {@code "repo:read"}, {@code "repo:write"},
 * {@code "user:read"}) that restrict what operations the token may perform.
 */
@Entity
@Table(name = "personal_tokens")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PersonalToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The ID of the user this token belongs to. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Human-readable label for the token (e.g. "CI pipeline", "Local dev"). */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * SHA-256 hex digest of the raw token value.
     * Stored instead of the raw value so that a database breach cannot be used to impersonate users.
     */
    @Column(name = "token_hash", nullable = false, unique = true, length = 255)
    private String tokenHash;

    /**
     * Declared scopes for this token (e.g. {@code ["repo:read", "repo:write"]}).
     * Stored as a PostgreSQL {@code TEXT[]} array column.
     */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "scopes", nullable = false, columnDefinition = "TEXT[]")
    private List<String> scopes;

    /**
     * Optional absolute expiry time. When {@code null} the token never expires.
     * Tokens past this instant are invalid.
     */
    @Column(name = "expires_at", columnDefinition = "TIMESTAMPTZ")
    private Instant expiresAt;

    /** Timestamp when this token was created. */
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}
