package com.dvcs.repository.repository;

import com.dvcs.repository.domain.Repository;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Repository} entities.
 */
@org.springframework.stereotype.Repository
public interface RepoRepository extends JpaRepository<Repository, Long> {

    /**
     * Finds a repository by its owner's user ID and repository name.
     *
     * @param ownerId the owner's user ID
     * @param name    the repository name
     * @return an {@link Optional} containing the matching repository, or empty if none found
     */
    Optional<Repository> findByOwnerIdAndName(Long ownerId, String name);

    /**
     * Finds all repositories owned by the given user ID.
     *
     * @param ownerId the owner's user ID
     * @return list of repositories owned by the user
     */
    List<Repository> findByOwnerId(Long ownerId);
}
