package com.dvcs.repository.repository;

import com.dvcs.repository.domain.Collaborator;
import com.dvcs.repository.domain.CollaboratorId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Collaborator} entities.
 */
@Repository
public interface CollaboratorRepository extends JpaRepository<Collaborator, CollaboratorId> {

    /**
     * Finds a collaborator entry by repository ID and user ID.
     *
     * @param repoId the repository ID
     * @param userId the user ID
     * @return an {@link Optional} containing the collaborator, or empty if none found
     */
    Optional<Collaborator> findByRepoIdAndUserId(Long repoId, Long userId);

    /**
     * Checks whether a collaborator row exists for the given repository, user,
     * and one of the specified roles.
     *
     * @param repoId the repository ID
     * @param userId the user ID
     * @param roles  the list of acceptable roles (e.g. {@code ["OWNER", "WRITE"]})
     * @return {@code true} if a matching row exists
     */
    boolean existsByRepoIdAndUserIdAndRoleIn(Long repoId, Long userId, List<String> roles);
}
