package com.dvcs.pullrequest.repository;

import com.dvcs.pullrequest.domain.PullRequest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PullRequest} entities.
 */
@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    /**
     * Finds pull requests for a repository filtered by status, with pagination.
     *
     * @param repoId   the repository ID
     * @param status   the PR status (open, closed, merged)
     * @param pageable pagination parameters
     * @return page of pull requests
     */
    Page<PullRequest> findByRepoIdAndStatus(Long repoId, String status, Pageable pageable);

    /**
     * Finds a pull request by repository ID and PR number.
     *
     * @param repoId the repository ID
     * @param number the sequential PR number within the repository
     * @return an {@link Optional} containing the pull request, or empty if not found
     */
    Optional<PullRequest> findByRepoIdAndNumber(Long repoId, Integer number);

    /**
     * Finds all open pull requests for a repository with a specific head branch.
     *
     * @param repoId     the repository ID
     * @param headBranch the head branch name
     * @return list of open pull requests with the given head branch
     */
    List<PullRequest> findByRepoIdAndStatusAndHeadBranch(Long repoId, String status, String headBranch);

    /**
     * Computes the next sequential PR number for a repository.
     *
     * @param repoId the repository ID
     * @return the next PR number (max + 1, or 1 if no PRs exist)
     */
    @Query("SELECT COALESCE(MAX(pr.number), 0) + 1 FROM PullRequest pr WHERE pr.repoId = :repoId")
    int findNextNumber(@Param("repoId") Long repoId);
}
