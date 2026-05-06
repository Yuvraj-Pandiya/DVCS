package com.dvcs.repository.repository;

import com.dvcs.repository.domain.CommitMeta;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link CommitMeta} entities.
 */
@Repository
public interface CommitMetaRepository extends JpaRepository<CommitMeta, Long> {

    /**
     * Finds all commits for a repository ordered by authored date descending (newest first).
     *
     * @param repoId   the repository ID
     * @param pageable pagination parameters
     * @return page of commit metadata
     */
    Page<CommitMeta> findByRepoIdOrderByAuthoredAtDesc(Long repoId, Pageable pageable);

    /**
     * Finds a specific commit by repository ID and SHA.
     *
     * @param repoId the repository ID
     * @param sha    the commit SHA
     * @return an {@link Optional} containing the commit, or empty if not found
     */
    Optional<CommitMeta> findByRepoIdAndSha(Long repoId, String sha);

    /**
     * Counts the total number of commits for a repository.
     *
     * @param repoId the repository ID
     * @return commit count
     */
    long countByRepoId(Long repoId);

    /**
     * Finds commits reachable from head SHA but not from base SHA.
     * This is a simplified implementation that returns commits after the base commit.
     *
     * @param repoId  the repository ID
     * @param headSha the head commit SHA
     * @param baseSha the base commit SHA
     * @return page of commits
     */
    @Query(value = """
            SELECT c.* FROM commits_meta c
            WHERE c.repo_id = :repoId
              AND c.authored_at > (
                  SELECT authored_at FROM commits_meta
                  WHERE repo_id = :repoId AND sha = :baseSha
              )
              AND c.authored_at <= (
                  SELECT authored_at FROM commits_meta
                  WHERE repo_id = :repoId AND sha = :headSha
              )
            ORDER BY c.authored_at DESC
            """,
            nativeQuery = true)
    java.util.List<CommitMeta> findCommitsBetween(@Param("repoId") Long repoId,
                                                   @Param("baseSha") String baseSha,
                                                   @Param("headSha") String headSha);
}
