package com.dvcs.git.commit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CommitMeta} entities.
 *
 * <p>Requirement 6: HTTP Smart Git Transport — commit metadata persistence on push.
 */
@Repository
public interface CommitMetaRepository extends JpaRepository<CommitMeta, Long> {

    /**
     * Finds a commit by repository ID and SHA.
     *
     * @param repoId the repository ID
     * @param sha    the commit SHA-256 hex digest
     * @return an {@link Optional} containing the commit metadata, or empty if not found
     */
    Optional<CommitMeta> findByRepoIdAndSha(Long repoId, String sha);

    /**
     * Returns a paginated list of commits for a repository, ordered by author timestamp
     * descending (newest first).
     *
     * @param repoId   the repository ID
     * @param pageable pagination parameters
     * @return list of commit metadata records
     */
    List<CommitMeta> findByRepoIdOrderByAuthoredAtDesc(Long repoId, Pageable pageable);
}
