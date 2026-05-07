package com.dvcs.pullrequest.service;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.exception.InvalidRequestException;
import com.dvcs.pullrequest.domain.PrReview;
import com.dvcs.pullrequest.domain.PullRequest;
import com.dvcs.pullrequest.repository.PrReviewRepository;
import com.dvcs.pullrequest.repository.PullRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Service for managing pull request reviews and mergeability checks.
 *
 * <p>Handles review submission, verdict validation, and computing whether
 * a PR is mergeable based on the latest review from each reviewer.
 *
 * <p>Requirement 10.5: ReviewService.
 */
@Service
@Transactional
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private static final Set<String> VALID_VERDICTS = Set.of("APPROVE", "CHANGES_REQUESTED", "COMMENT");

    private final PrReviewRepository prReviewRepository;
    private final PullRequestRepository pullRequestRepository;

    /**
     * Optional notification service — injected if available, otherwise notifications
     * are silently skipped. This avoids a hard dependency on the notification module.
     */
    @Autowired(required = false)
    private NotificationPort notificationPort;

    public ReviewService(PrReviewRepository prReviewRepository,
                         PullRequestRepository pullRequestRepository) {
        this.prReviewRepository = prReviewRepository;
        this.pullRequestRepository = pullRequestRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Submits a review on a pull request.
     *
     * <p>Validates the verdict, saves the review, and notifies the PR author
     * if the reviewer is a different user.
     *
     * @param userId  the ID of the reviewer
     * @param prId    the pull request ID
     * @param verdict the review verdict (APPROVE, CHANGES_REQUESTED, or COMMENT)
     * @param body    the optional review body text
     * @return the saved {@link PrReview} entity
     * @throws InvalidRequestException if the verdict is not valid
     * @throws EntityNotFoundException if the PR does not exist
     */
    public PrReview submitReview(Long userId, Long prId, String verdict, String body) {
        if (!VALID_VERDICTS.contains(verdict)) {
            throw new InvalidRequestException(
                    "Invalid verdict '" + verdict + "'. Must be one of: APPROVE, CHANGES_REQUESTED, COMMENT");
        }

        PullRequest pr = pullRequestRepository.findById(prId)
                .orElseThrow(() -> new EntityNotFoundException("Pull request with id " + prId + " not found"));

        PrReview review = PrReview.builder()
                .prId(prId)
                .reviewerId(userId)
                .verdict(verdict)
                .body(body)
                .build();

        PrReview saved = prReviewRepository.save(review);

        // Notify PR author if reviewer is a different user
        if (!userId.equals(pr.getAuthorId())) {
            notifyPrAuthor(pr, userId, verdict);
        }

        return saved;
    }

    /**
     * Determines whether a pull request is mergeable.
     *
     * <p>A PR is mergeable if:
     * <ul>
     *   <li>At least one reviewer has submitted an APPROVE as their latest verdict, AND</li>
     *   <li>No reviewer's latest verdict is CHANGES_REQUESTED.</li>
     * </ul>
     *
     * <p>Only the most recent review from each reviewer is considered.
     *
     * @param prId the pull request ID
     * @return {@code true} if the PR is mergeable; {@code false} otherwise
     */
    @Transactional(readOnly = true)
    public boolean isMergeable(Long prId) {
        List<PrReview> allReviews = prReviewRepository.findByPrId(prId);

        if (allReviews.isEmpty()) {
            return false;
        }

        // Collect unique reviewer IDs
        Set<Long> reviewerIds = new HashSet<>();
        for (PrReview review : allReviews) {
            reviewerIds.add(review.getReviewerId());
        }

        boolean hasApproval = false;
        boolean hasChangesRequested = false;

        // For each reviewer, find their latest verdict
        for (Long reviewerId : reviewerIds) {
            Optional<PrReview> latestReview = prReviewRepository
                    .findTopByPrIdAndReviewerIdOrderBySubmittedAtDesc(prId, reviewerId);

            if (latestReview.isPresent()) {
                String latestVerdict = latestReview.get().getVerdict();
                if ("APPROVE".equals(latestVerdict)) {
                    hasApproval = true;
                } else if ("CHANGES_REQUESTED".equals(latestVerdict)) {
                    hasChangesRequested = true;
                }
            }
        }

        return hasApproval && !hasChangesRequested;
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Sends a notification to the PR author about a new review.
     *
     * <p>Notification delivery is best-effort; failures are logged but not propagated.
     *
     * @param pr        the pull request
     * @param reviewerId the reviewer's user ID
     * @param verdict   the review verdict
     */
    private void notifyPrAuthor(PullRequest pr, Long reviewerId, String verdict) {
        if (notificationPort == null) {
            log.debug("NotificationPort not available — skipping notification for PR #{}", pr.getNumber());
            return;
        }
        try {
            String reason = "review_" + verdict.toLowerCase();
            notificationPort.createNotification(pr.getAuthorId(), "pull_request", pr.getId(), reason);
        } catch (Exception e) {
            log.warn("Failed to send review notification for PR #{}: {}", pr.getNumber(), e.getMessage());
        }
    }
}
