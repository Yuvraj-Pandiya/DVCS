package com.dvcs.auth.repository;

import com.dvcs.auth.domain.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link RefreshToken} entities.
 */
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Looks up a refresh token by its SHA-256 hash.
     *
     * @param tokenHash the SHA-256 hex digest of the raw token
     * @return an {@link Optional} containing the matching token record, or empty if none found
     */
    Optional<RefreshToken> findByTokenHash(String tokenHash);
}
