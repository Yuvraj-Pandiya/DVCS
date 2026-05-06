package com.dvcs.auth.service;

import com.dvcs.auth.domain.SshKey;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.exception.ConflictException;
import com.dvcs.auth.repository.SshKeyRepository;
import com.dvcs.auth.repository.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * Service for managing SSH public keys associated with user accounts.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate and compute the SHA-256 fingerprint of submitted public keys.</li>
 *   <li>Reject duplicate keys (same fingerprint) for the same user.</li>
 *   <li>Persist new keys and expose list/delete operations.</li>
 * </ul>
 */
@Service
@Transactional
public class SshKeyService {

    private final SshKeyRepository sshKeyRepository;
    private final UserRepository userRepository;

    public SshKeyService(SshKeyRepository sshKeyRepository, UserRepository userRepository) {
        this.sshKeyRepository = sshKeyRepository;
        this.userRepository = userRepository;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers a new SSH public key for the given user.
     *
     * <p>The fingerprint is computed as a colon-separated SHA-256 hex digest of the
     * public key bytes (UTF-8). If a key with the same fingerprint already exists for
     * this user a {@link ConflictException} is thrown.
     *
     * @param userId    the ID of the user adding the key
     * @param title     a human-readable label for the key (e.g. "Work laptop")
     * @param publicKey the full SSH public key string (e.g. {@code ssh-rsa AAAA...})
     * @return the newly created and persisted {@link SshKey}
     * @throws IllegalArgumentException if {@code publicKey} is blank
     * @throws ConflictException        if the user already has a key with the same fingerprint
     * @throws EntityNotFoundException  if no user with {@code userId} exists
     */
    public SshKey addKey(Long userId, String title, String publicKey) {
        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException("Public key must not be blank.");
        }

        String fingerprint = computeFingerprint(publicKey);

        if (sshKeyRepository.existsByUserIdAndFingerprint(userId, fingerprint)) {
            throw new ConflictException(
                    "An SSH key with this fingerprint already exists for the user.");
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "User not found with id: " + userId));

        SshKey sshKey = SshKey.builder()
                .user(user)
                .title(title)
                .publicKey(publicKey)
                .fingerprint(fingerprint)
                .build();

        return sshKeyRepository.save(sshKey);
    }

    /**
     * Returns all SSH keys registered by the given user.
     *
     * @param userId the ID of the user whose keys to list
     * @return list of SSH keys, possibly empty
     */
    @Transactional(readOnly = true)
    public List<SshKey> listKeys(Long userId) {
        return sshKeyRepository.findByUserId(userId);
    }

    /**
     * Deletes an SSH key by ID, verifying that it belongs to the requesting user.
     *
     * @param userId the ID of the user requesting deletion
     * @param keyId  the ID of the SSH key to delete
     * @throws EntityNotFoundException  if no key with {@code keyId} exists
     * @throws SecurityException        if the key does not belong to {@code userId}
     */
    public void deleteKey(Long userId, Long keyId) {
        SshKey key = sshKeyRepository.findById(keyId)
                .orElseThrow(() -> new EntityNotFoundException(
                        "SSH key not found with id: " + keyId));

        if (!key.getUser().getId().equals(userId)) {
            throw new SecurityException(
                    "Access denied: SSH key " + keyId + " does not belong to user " + userId);
        }

        sshKeyRepository.delete(key);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Computes the colon-separated SHA-256 fingerprint of the given public key string.
     *
     * <p>The key is encoded as UTF-8 bytes, hashed with SHA-256, and the resulting
     * 32-byte digest is formatted as 32 lowercase hex pairs separated by colons
     * (e.g. {@code "aa:bb:cc:..."}).
     *
     * @param publicKey the SSH public key string
     * @return colon-separated SHA-256 hex fingerprint
     */
    static String computeFingerprint(String publicKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(publicKey.getBytes(StandardCharsets.UTF_8));
            String hex = HexFormat.of().formatHex(hashBytes);
            // Insert colons between every two hex characters: "aabb" → "aa:bb"
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < hex.length(); i += 2) {
                if (sb.length() > 0) {
                    sb.append(':');
                }
                sb.append(hex, i, i + 2);
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM (JCA spec)
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
