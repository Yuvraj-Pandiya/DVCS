package com.dvcs.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

/**
 * JPA entity representing an SSH public key registered by a user, mapped to the {@code ssh_keys} table.
 *
 * <p>The fingerprint is a colon-separated SHA-256 hex digest of the raw public key bytes,
 * computed at service layer before persistence. The combination of {@code user_id} and
 * {@code fingerprint} is unique, preventing duplicate keys per user.
 */
@Entity
@Table(name = "ssh_keys")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SshKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who owns this SSH key. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** Human-readable label for the key (e.g. "Work laptop", "Home desktop"). */
    @Column(name = "title", nullable = false, length = 128)
    private String title;

    /** The full SSH public key string (e.g. {@code ssh-rsa AAAA...}). */
    @Column(name = "public_key", nullable = false, columnDefinition = "TEXT")
    private String publicKey;

    /**
     * Colon-separated SHA-256 hex fingerprint of the public key bytes
     * (e.g. {@code "aa:bb:cc:..."}). Used to detect duplicate keys per user.
     */
    @Column(name = "fingerprint", nullable = false, length = 128)
    private String fingerprint;

    /** Timestamp when this key was registered. Set automatically on first persist. */
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private Instant createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }
}
