package com.dvcs.auth.repository;

import com.dvcs.auth.domain.SshKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link SshKey} entities.
 *
 * <p>Derived query methods are resolved automatically by Spring Data JPA
 * based on the field names declared in {@link SshKey}.
 */
public interface SshKeyRepository extends JpaRepository<SshKey, Long> {

    /**
     * Returns all SSH keys registered by the given user.
     *
     * @param userId the ID of the owning user
     * @return list of SSH keys belonging to that user, possibly empty
     */
    List<SshKey> findByUserId(Long userId);

    /**
     * Looks up a specific SSH key by user and fingerprint.
     *
     * @param userId      the ID of the owning user
     * @param fingerprint the colon-separated SHA-256 fingerprint
     * @return an {@link Optional} containing the matching key, or empty if none found
     */
    Optional<SshKey> findByUserIdAndFingerprint(Long userId, String fingerprint);

    /**
     * Checks whether a key with the given fingerprint already exists for the user.
     *
     * @param userId      the ID of the owning user
     * @param fingerprint the colon-separated SHA-256 fingerprint
     * @return {@code true} if a duplicate exists, {@code false} otherwise
     */
    boolean existsByUserIdAndFingerprint(Long userId, String fingerprint);
}
