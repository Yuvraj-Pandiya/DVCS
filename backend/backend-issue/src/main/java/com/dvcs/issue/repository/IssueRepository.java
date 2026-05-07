package com.dvcs.issue.repository;

import com.dvcs.issue.domain.Issue;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Issue} entities.
 */
@Repository
public interface IssueRepository extends JpaRepository<Issue, Long> {

    /**
     * Finds all issues for a repository, with pagination.
     *
     * @param repoId   the repository ID
     * @param pageable pagination parameters
     * @return page of issues
     */
    Page<Issue> findByRepoId(Long repoId, Pageable pageable);

    /**
     * Finds issues for a repository filtered by status, with pagination.
     *
     * @param repoId   the repository ID
     * @param status   the issue status ({@code open} or {@code closed})
     * @param pageable pagination parameters
     * @return page of issues
     */
    Page<Issue> findByRepoIdAndStatus(Long repoId, String status, Pageable pageable);

    /**
     * Finds an issue by repository ID and issue number.
     *
     * @param repoId the repository ID
     * @param number the sequential issue number within the repository
     * @return an {@link Optional} containing the issue, or empty if not found
     */
    Optional<Issue> findByRepoIdAndNumber(Long repoId, Integer number);

    /**
     * Computes the next sequential issue number for a repository.
     *
     * @param repoId the repository ID
     * @return the next issue number (max + 1, or 1 if no issues exist)
     */
    @Query("SELECT COALESCE(MAX(i.number), 0) + 1 FROM Issue i WHERE i.repoId = :repoId")
    Integer findNextNumber(@Param("repoId") Long repoId);
}
