package com.dvcs.git.ref;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Branch} entities.
 *
 * <p>Requirement 5: Branch and Tag Reference Management.
 */
@Repository("gitBranchRepository")
public interface BranchRepository extends JpaRepository<Branch, Long> {

    /**
     * Returns all branches belonging to the given repository.
     *
     * @param repoId the repository ID
     * @return list of branches; empty if none exist
     */
    List<Branch> findByRepoId(Long repoId);

    /**
     * Finds a branch by repository ID and branch name.
     *
     * @param repoId the repository ID
     * @param name   the branch name
     * @return an {@link Optional} containing the branch, or empty if not found
     */
    Optional<Branch> findByRepoIdAndName(Long repoId, String name);
}
