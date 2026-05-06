package com.dvcs.repository.repository;

import com.dvcs.repository.domain.Branch;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Branch} entities.
 */
@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Finds a branch by repository ID and branch name.
     *
     * @param repoId the repository ID
     * @param name   the branch name
     * @return an {@link Optional} containing the branch, or empty if not found
     */
    Optional<Branch> findByRepoIdAndName(Long repoId, String name);

    /**
     * Finds all branches for a given repository.
     *
     * @param repoId the repository ID
     * @return list of branches
     */
    List<Branch> findByRepoId(Long repoId);
}
