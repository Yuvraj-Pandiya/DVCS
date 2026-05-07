package com.dvcs.pullrequest.service;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.exception.InvalidRequestException;
import com.dvcs.common.validation.InputSanitizer;
import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.service.DiffService;
import com.dvcs.pullrequest.domain.PrComment;
import com.dvcs.pullrequest.domain.PrReview;
import com.dvcs.pullrequest.domain.PullRequest;
import com.dvcs.pullrequest.dto.CreatePrRequest;
import com.dvcs.pullrequest.dto.PrDetailResponse;
import com.dvcs.pullrequest.dto.PrListItemDto;
import com.dvcs.pullrequest.repository.PrCommentRepository;
import com.dvcs.pullrequest.repository.PrReviewRepository;
import com.dvcs.pullrequest.repository.PullRequestRepository;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.repository.BranchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for managing pull request lifecycle operations.
 *
 * <p>Handles opening PRs, listing, retrieving details, and triggering diff
 * recomputation when the head branch is updated.
 *
 * <p>Requirement 10: Pull Request Lifecycle.
 */
@Service
@Transactional
public class PullRequestService {

    private static final Logger log = LoggerFactory.getLogger(PullRequestService.class);

    private final PullRequestRepository pullRequestRepository;
    private final PrReviewRepository prReviewRepository;
    private final PrCommentRepository prCommentRepository;
    private final BranchRepository branchRepository;
    private final DiffService diffService;
    private final InputSanitizer inputSanitizer;
    private final UserRepository userRepository;

    public PullRequestService(PullRequestRepository pullRequestRepository,
                               PrReviewRepository prReviewRepository,
                               PrCommentRepository prCommentRepository,
                               BranchRepository branchRepository,
                               DiffService diffService,
                               InputSanitizer inputSanitizer,
                               UserRepository userRepository) {
        this.pullRequestRepository = pullRequestRepository;
        this.prReviewRepository = prReviewRepository;
        this.prCommentRepository = prCommentRepository;
        this.branchRepository = branchRepository;
        this.diffService = diffService;
        this.inputSanitizer = inputSanitizer;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Opens a new pull request.
     *
     * <p>Validates that head and base branches are different, assigns a sequential
     * PR number, and persists the new PR with status {@code open}.
     *
     * @param userId the ID of the user opening the PR
     * @param repoId the repository ID
     * @param req    the PR creation request
     * @return the saved {@link PullRequest} entity
     * @throws InvalidRequestException if head branch equals base branch
     */
    public PullRequest openPr(Long userId, Long repoId, CreatePrRequest req) {
        if (req.headBranch().equals(req.baseBranch())) {
            throw new InvalidRequestException(
                    "Head branch and base branch must be different. Both are: '" + req.headBranch() + "'");
        }

        int nextNumber = pullRequestRepository.findNextNumber(repoId);

        PullRequest pr = PullRequest.builder()
                .repoId(repoId)
                .number(nextNumber)
                .title(req.title())
                .body(inputSanitizer.sanitize(req.body()))
                .headBranch(req.headBranch())
                .baseBranch(req.baseBranch())
                .authorId(userId)
                .status("open")
                .build();

        return pullRequestRepository.save(pr);
    }

    /**
     * Lists pull requests for a repository filtered by status.
     *
     * <p>Returns enriched DTOs with author username, labels, and review status.
     *
     * @param repoId   the repository ID
     * @param status   the PR status filter (open, closed, merged)
     * @param pageable pagination parameters
     * @return page of enriched pull request DTOs
     */
    @Transactional(readOnly = true)
    public Page<PrListItemDto> listPrs(Long repoId, String status, Pageable pageable) {
        Page<PullRequest> prPage = pullRequestRepository.findByRepoIdAndStatus(repoId, status, pageable);
        
        if (prPage.isEmpty()) {
            return Page.empty(pageable);
        }

        // Collect all author IDs
        Set<Long> authorIds = prPage.getContent().stream()
                .map(PullRequest::getAuthorId)
                .collect(Collectors.toSet());

        // Fetch all authors in one query
        Map<Long, String> authorUsernameMap = userRepository.findAllById(authorIds).stream()
                .collect(Collectors.toMap(User::getId, User::getUsername));

        // Collect all PR IDs for review status lookup
        List<Long> prIds = prPage.getContent().stream()
                .map(PullRequest::getId)
                .toList();

        // Fetch review counts for all PRs in one query
        Map<Long, PrListItemDto.ReviewStatusDto> reviewStatusMap = buildReviewStatusMap(prIds);

        // Transform to DTOs
        List<PrListItemDto> dtos = prPage.getContent().stream()
                .map(pr -> new PrListItemDto(
                        pr.getId(),
                        pr.getNumber(),
                        pr.getTitle(),
                        pr.getBody(),
                        pr.getHeadBranch(),
                        pr.getBaseBranch(),
                        pr.getAuthorId(),
                        authorUsernameMap.getOrDefault(pr.getAuthorId(), "unknown"),
                        pr.getStatus(),
                        pr.getMergedAt(),
                        pr.getCreatedAt(),
                        Collections.emptyList(), // TODO: fetch labels when pr_labels table is populated
                        reviewStatusMap.getOrDefault(pr.getId(), emptyReviewStatus())
                ))
                .toList();

        return new PageImpl<>(dtos, pageable, prPage.getTotalElements());
    }

    /**
     * Builds a map of PR ID to review status summary.
     *
     * @param prIds list of PR IDs
     * @return map of PR ID to ReviewStatusDto
     */
    private Map<Long, PrListItemDto.ReviewStatusDto> buildReviewStatusMap(List<Long> prIds) {
        if (prIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<PrReview> allReviews = prReviewRepository.findByPrIdIn(prIds);
        
        Map<Long, PrListItemDto.ReviewStatusDto> statusMap = new HashMap<>();
        
        // Group reviews by PR ID
        Map<Long, List<PrReview>> reviewsByPr = allReviews.stream()
                .collect(Collectors.groupingBy(PrReview::getPrId));

        // Calculate status for each PR
        for (Long prId : prIds) {
            List<PrReview> reviews = reviewsByPr.getOrDefault(prId, Collections.emptyList());
            
            int approveCount = 0;
            int changesRequestedCount = 0;
            int commentCount = 0;

            for (PrReview review : reviews) {
                switch (review.getVerdict()) {
                    case "APPROVE" -> approveCount++;
                    case "CHANGES_REQUESTED" -> changesRequestedCount++;
                    case "COMMENT" -> commentCount++;
                }
            }

            statusMap.put(prId, new PrListItemDto.ReviewStatusDto(
                    approveCount,
                    changesRequestedCount,
                    commentCount,
                    approveCount > 0,
                    changesRequestedCount > 0
            ));
        }

        return statusMap;
    }

    /**
     * Returns an empty review status (no reviews).
     */
    private PrListItemDto.ReviewStatusDto emptyReviewStatus() {
        return new PrListItemDto.ReviewStatusDto(0, 0, 0, false, false);
    }

    /**
     * Retrieves full pull request detail including diff, reviews, and comments.
     *
     * @param repoId   the repository ID
     * @param prNumber the sequential PR number within the repository
     * @return a {@link PrDetailResponse} with PR metadata, diff, reviews, and comments
     * @throws EntityNotFoundException if the PR does not exist
     */
    @Transactional(readOnly = true)
    public PrDetailResponse getPrDetail(Long repoId, Integer prNumber) {
        PullRequest pr = pullRequestRepository.findByRepoIdAndNumber(repoId, prNumber)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Pull request #" + prNumber + " not found in repository " + repoId));

        List<PrReview> reviews = prReviewRepository.findByPrId(pr.getId());
        List<PrComment> comments = prCommentRepository.findByPrIdOrderByCreatedAtAsc(pr.getId());

        // Compute diff between head and base branch tips
        List<DiffHunk> diff = computeDiff(repoId, pr);

        return new PrDetailResponse(pr, diff, reviews, comments);
    }

    /**
     * Recomputes the diff for all open pull requests whose head branch matches
     * the given branch name.
     *
     * <p>Called when a push is made to a branch that is the head of one or more
     * open PRs. The actual diff recomputation is logged here; the diff is computed
     * lazily on {@link #getPrDetail} calls.
     *
     * @param repoId     the repository ID
     * @param branchName the branch that was pushed to
     */
    public void updateDiffOnPush(Long repoId, String branchName) {
        List<PullRequest> affectedPrs = pullRequestRepository
                .findByRepoIdAndStatusAndHeadBranch(repoId, "open", branchName);

        if (affectedPrs.isEmpty()) {
            return;
        }

        log.info("Push to branch '{}' in repo {} affects {} open PR(s). Diff will be recomputed on next detail request.",
                branchName, repoId, affectedPrs.size());

        // Diff is computed lazily on getPrDetail; no snapshot storage needed.
        // If a diff snapshot cache were maintained, it would be invalidated here.
        for (PullRequest pr : affectedPrs) {
            log.debug("PR #{} (id={}) head branch '{}' updated — diff snapshot invalidated.",
                    pr.getNumber(), pr.getId(), branchName);
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes the diff between the head and base branch tips for a pull request.
     *
     * <p>If the branches cannot be resolved or the diff computation fails,
     * an empty list is returned and the error is logged.
     *
     * @param repoId the repository ID
     * @param pr     the pull request
     * @return list of diff hunks, or empty list on error
     */
    private List<DiffHunk> computeDiff(Long repoId, PullRequest pr) {
        try {
            Optional<Branch> headBranchOpt = branchRepository.findByRepoIdAndName(repoId, pr.getHeadBranch());
            Optional<Branch> baseBranchOpt = branchRepository.findByRepoIdAndName(repoId, pr.getBaseBranch());

            if (headBranchOpt.isEmpty() || baseBranchOpt.isEmpty()) {
                log.warn("Cannot compute diff for PR #{}: branch not found (head={}, base={})",
                        pr.getNumber(), pr.getHeadBranch(), pr.getBaseBranch());
                return Collections.emptyList();
            }

            String headSha = headBranchOpt.get().getHeadSha();
            String baseSha = baseBranchOpt.get().getHeadSha();

            // Compute diff for the root path (all files)
            // DiffService.textDiff requires a specific file path; for a full PR diff
            // we return an empty list here as a placeholder — full tree diff would
            // require iterating all changed files between the two commits.
            // TODO: implement full tree diff by walking commit trees and diffing each changed file
            log.debug("PR #{} diff: base={} head={} (full tree diff not yet implemented, returning empty)",
                    pr.getNumber(), baseSha, headSha);
            return Collections.emptyList();

        } catch (Exception e) {
            log.warn("Failed to compute diff for PR #{}: {}", pr.getNumber(), e.getMessage());
            return Collections.emptyList();
        }
    }
}
