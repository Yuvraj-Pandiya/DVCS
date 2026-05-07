package com.dvcs.pullrequest.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.exception.InvalidRequestException;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.pullrequest.domain.PrComment;
import com.dvcs.pullrequest.domain.PrReview;
import com.dvcs.pullrequest.domain.PullRequest;
import com.dvcs.pullrequest.dto.AddCommentRequest;
import com.dvcs.pullrequest.dto.CreatePrRequest;
import com.dvcs.pullrequest.dto.PrDetailResponse;
import com.dvcs.pullrequest.dto.PrListItemDto;
import com.dvcs.pullrequest.dto.SubmitReviewRequest;
import com.dvcs.pullrequest.repository.PrCommentRepository;
import com.dvcs.pullrequest.service.MergeStrategyService;
import com.dvcs.pullrequest.service.PullRequestService;
import com.dvcs.pullrequest.service.ReviewService;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.RepoRepository;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for pull request operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/repos/{owner}/{repo}/pulls — open a new PR</li>
 *   <li>GET /api/repos/{owner}/{repo}/pulls — list PRs by status</li>
 *   <li>GET /api/repos/{owner}/{repo}/pulls/{number} — get PR detail</li>
 *   <li>POST /api/repos/{owner}/{repo}/pulls/{number}/review — submit a review</li>
 *   <li>POST /api/repos/{owner}/{repo}/pulls/{number}/merge — merge a PR</li>
 *   <li>POST /api/repos/{owner}/{repo}/pulls/{number}/comments — add a comment</li>
 * </ul>
 *
 * <p>Requirement 10: Pull Request Lifecycle.
 */
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/pulls")
public class PullRequestController {

    private final PullRequestService pullRequestService;
    private final ReviewService reviewService;
    private final MergeStrategyService mergeStrategyService;
    private final PrCommentRepository prCommentRepository;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;

    public PullRequestController(PullRequestService pullRequestService,
                                  ReviewService reviewService,
                                  MergeStrategyService mergeStrategyService,
                                  PrCommentRepository prCommentRepository,
                                  RepoRepository repoRepository,
                                  UserRepository userRepository) {
        this.pullRequestService = pullRequestService;
        this.reviewService = reviewService;
        this.mergeStrategyService = mergeStrategyService;
        this.prCommentRepository = prCommentRepository;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/pulls
    // =========================================================================

    /**
     * Opens a new pull request.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param request        the PR creation request
     * @param authentication the current authentication
     * @return HTTP 201 with the created pull request
     */
    @PostMapping
    @PreAuthorize("@repoAccessGuard.canWrite(authentication, #owner, #repo)")
    public ResponseEntity<PullRequest> openPr(
            @PathVariable String owner,
            @PathVariable String repo,
            @Valid @RequestBody CreatePrRequest request,
            Authentication authentication) {

        User user = extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        PullRequest pr = pullRequestService.openPr(user.getId(), repoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(pr);
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/pulls
    // =========================================================================

    /**
     * Lists pull requests for a repository, filtered by status.
     *
     * @param owner  the repository owner's username
     * @param repo   the repository name
     * @param status the PR status filter (default: "open")
     * @param page   the page number (0-based, default: 0)
     * @param size   the page size (default: 20)
     * @return HTTP 200 with a page of pull request DTOs
     */
    @GetMapping
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<Page<PrListItemDto>> listPrs(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(defaultValue = "open") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long repoId = resolveRepoId(owner, repo);
        Pageable pageable = PageRequest.of(page, size);
        Page<PrListItemDto> prs = pullRequestService.listPrs(repoId, status, pageable);
        return ResponseEntity.ok(prs);
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/pulls/{number}
    // =========================================================================

    /**
     * Retrieves full pull request detail including diff, reviews, and comments.
     *
     * @param owner  the repository owner's username
     * @param repo   the repository name
     * @param number the sequential PR number
     * @return HTTP 200 with the PR detail response
     */
    @GetMapping("/{number}")
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<PrDetailResponse> getPrDetail(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number) {

        Long repoId = resolveRepoId(owner, repo);
        PrDetailResponse detail = pullRequestService.getPrDetail(repoId, number);
        return ResponseEntity.ok(detail);
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/pulls/{number}/review
    // =========================================================================

    /**
     * Submits a review on a pull request.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential PR number
     * @param request        the review submission request
     * @param authentication the current authentication
     * @return HTTP 201 with the saved review
     */
    @PostMapping("/{number}/review")
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<PrReview> submitReview(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @Valid @RequestBody SubmitReviewRequest request,
            Authentication authentication) {

        User user = extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        PullRequest pr = pullRequestService.getPrDetail(repoId, number).pr();
        PrReview review = reviewService.submitReview(user.getId(), pr.getId(),
                request.verdict(), request.body());
        return ResponseEntity.status(HttpStatus.CREATED).body(review);
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/pulls/{number}/merge
    // =========================================================================

    /**
     * Merges a pull request using the specified strategy.
     *
     * <p>Checks mergeability before proceeding. Returns HTTP 422 if the PR
     * is not mergeable (no APPROVE or unresolved CHANGES_REQUESTED).
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential PR number
     * @param strategy       the merge strategy (merge-commit, squash, rebase; default: merge-commit)
     * @param authentication the current authentication
     * @return HTTP 200 on successful merge
     */
    @PostMapping("/{number}/merge")
    @PreAuthorize("@repoAccessGuard.canWrite(authentication, #owner, #repo)")
    public ResponseEntity<Void> merge(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @RequestParam(defaultValue = "merge-commit") String strategy,
            Authentication authentication) {

        User user = extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        PullRequest pr = pullRequestService.getPrDetail(repoId, number).pr();

        if (!"open".equals(pr.getStatus())) {
            throw new InvalidRequestException(
                    "Pull request #" + number + " is not open (status: " + pr.getStatus() + ")");
        }

        if (!reviewService.isMergeable(pr.getId())) {
            throw new InvalidRequestException(
                    "Pull request #" + number + " is not mergeable. "
                    + "It requires at least one APPROVE review and no unresolved CHANGES_REQUESTED reviews.");
        }

        switch (strategy) {
            case "squash" -> mergeStrategyService.squashMerge(repoId, pr, user.getId());
            case "rebase" -> mergeStrategyService.rebaseMerge(repoId, pr, user.getId());
            default -> mergeStrategyService.mergeCommit(repoId, pr, user.getId());
        }

        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/pulls/{number}/comments
    // =========================================================================

    /**
     * Adds a comment to a pull request.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential PR number
     * @param request        the comment request
     * @param authentication the current authentication
     * @return HTTP 201 with the saved comment
     */
    @PostMapping("/{number}/comments")
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<PrComment> addComment(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @Valid @RequestBody AddCommentRequest request,
            Authentication authentication) {

        User user = extractUser(authentication);
        Long repoId = resolveRepoId(owner, repo);
        PullRequest pr = pullRequestService.getPrDetail(repoId, number).pr();

        PrComment comment = PrComment.builder()
                .prId(pr.getId())
                .body(request.body())
                .filePath(request.filePath())
                .lineNumber(request.lineNumber())
                .authorId(user.getId())
                .build();

        PrComment saved = prCommentRepository.save(comment);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves the repository ID from owner username and repository name.
     *
     * @param owner    the owner's username
     * @param repoName the repository name
     * @return the repository ID
     * @throws EntityNotFoundException if the owner or repository does not exist
     */
    private Long resolveRepoId(String owner, String repoName) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        Repository repository = repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        return repository.getId();
    }

    /**
     * Extracts the authenticated user from the security context.
     *
     * @param authentication the current authentication
     * @return the authenticated user
     * @throws EntityNotFoundException if the user cannot be extracted
     */
    private User extractUser(Authentication authentication) {
        User user = RepoAccessGuard.extractUser(authentication);
        if (user == null) {
            throw new EntityNotFoundException("Authentication required.");
        }
        return user;
    }
}
