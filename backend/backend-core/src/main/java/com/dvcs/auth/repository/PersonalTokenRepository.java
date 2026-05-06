package com.dvcs.auth.repository;

import com.dvcs.auth.domain.PersonalToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PersonalToken} entities.
 */
public interface PersonalTokenRepository extends JpaRepository<PersonalToken, Long> {

    /**
     * Looks up a personal access token by its SHA-256 hash.
     *
     * @param tokenHash the SHA-256 hex digest of the raw token value
     * @return an {@link Optional} containing the matching token record, or empty if none found
     */
    Optional<PersonalToken> findByTokenHash(String tokenHash);
}
