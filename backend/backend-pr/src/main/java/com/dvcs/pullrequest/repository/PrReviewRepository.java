package com.dvcs.pullrequest.repository;

import com.dvcs.pullrequest.domain.PrReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link PrReview} entities.
 */
@Repository
public interface PrReviewRepository extends JpaRepository<PrReview, Long> {

    /**
     * Finds all reviews for a given pull request.
     *
     * @param prId the pull request ID
     * @return list of reviews for the PR
     */
    List<PrReview> findByPrId(Long prId);

    /**
     * Finds the most recent review submitted by a specific reviewer on a pull request.
     *
     * <p>Used to determine the latest verdict from each reviewer when computing
     * mergeability (a reviewer may submit multiple reviews over time).
     *
     * @param prId       the pull request ID
     * @param reviewerId the reviewer's user ID
     * @return an {@link Optional} containing the latest review, or empty if none found
     */
    Optional<PrReview> findTopByPrIdAndReviewerIdOrderBySubmittedAtDesc(Long prId, Long reviewerId);
}
