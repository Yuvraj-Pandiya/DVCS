package com.dvcs.issue.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.issue.dto.AddCommentRequest;
import com.dvcs.issue.dto.ApplyLabelRequest;
import com.dvcs.issue.dto.CreateIssueRequest;
import com.dvcs.issue.dto.IssueCommentResponse;
import com.dvcs.issue.dto.IssueResponse;
import com.dvcs.issue.dto.UpdateIssueRequest;
import com.dvcs.issue.service.IssueService;
import com.dvcs.issue.service.LabelService;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.RepoRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for issue lifecycle operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/repos/{owner}/{repo}/issues — create issue</li>
 *   <li>GET /api/repos/{owner}/{repo}/issues — list issues</li>
 *   <li>GET /api/repos/{owner}/{repo}/issues/{number} — get issue</li>
 *   <li>PATCH /api/repos/{owner}/{repo}/issues/{number} — update issue</li>
 *   <li>POST /api/repos/{owner}/{repo}/issues/{number}/close — close issue</li>
 *   <li>POST /api/repos/{owner}/{repo}/issues/{number}/comments — add comment</li>
 *   <li>POST /api/repos/{owner}/{repo}/issues/{number}/labels — apply label</li>
 *   <li>DELETE /api/repos/{owner}/{repo}/issues/{number}/labels/{labelId} — remove label</li>
 * </ul>
 *
 * <p>Requirement 11: Issue Tracker.
 */
@Tag(name = "Issues", description = "Issue tracker operations")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/issues")
public class IssueController {

    private final IssueService issueService;
    private final LabelService labelService;
    private final RepoRepository repoRepository;
    private final UserRepository userRepository;

    public IssueController(IssueService issueService,
                           LabelService labelService,
                           RepoRepository repoRepository,
                           UserRepository userRepository) {
        this.issueService = issueService;
        this.labelService = labelService;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/issues
    // =========================================================================

    /**
     * Creates a new issue in the repository.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param request        the issue creation request
     * @param authentication the current authentication
     * @return HTTP 201 with the created issue
     */
    @Operation(summary = "Create a new issue in the repository")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Issue created successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @PostMapping
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IssueResponse> createIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @Valid @RequestBody CreateIssueRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        IssueResponse response = issueService.createIssue(user.getId(), repoId, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/issues
    // =========================================================================

    /**
     * Lists issues for a repository, optionally filtered by status.
     *
     * @param owner    the repository owner's username
     * @param repo     the repository name
     * @param status   optional status filter ({@code open} or {@code closed})
     * @param pageable pagination parameters (default page size: 20)
     * @return HTTP 200 with a page of issues
     */
    @Operation(summary = "List issues for a repository, optionally filtered by status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issue list returned"),
        @ApiResponse(responseCode = "404", description = "Repository not found")
    })
    @GetMapping
    public ResponseEntity<Page<IssueResponse>> listIssues(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam(required = false) String status,
            @PageableDefault(size = 20) Pageable pageable) {

        Long repoId = resolveRepoId(owner, repo);
        return ResponseEntity.ok(issueService.listIssues(repoId, status, pageable));
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/issues/{number}
    // =========================================================================

    /**
     * Retrieves a single issue by its repository-scoped number.
     *
     * @param owner  the repository owner's username
     * @param repo   the repository name
     * @param number the sequential issue number
     * @return HTTP 200 with the issue
     */
    @Operation(summary = "Get a single issue by its sequential number")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issue returned"),
        @ApiResponse(responseCode = "404", description = "Repository or issue not found")
    })
    @GetMapping("/{number}")
    public ResponseEntity<IssueResponse> getIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number) {

        Long repoId = resolveRepoId(owner, repo);
        return ResponseEntity.ok(issueService.getIssue(repoId, number));
    }

    // =========================================================================
    // PATCH /api/repos/{owner}/{repo}/issues/{number}
    // =========================================================================

    /**
     * Updates the title and/or body of an issue.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential issue number
     * @param request        the update request
     * @param authentication the current authentication
     * @return HTTP 200 with the updated issue
     */
    @Operation(summary = "Update the title and/or body of an issue")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issue updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Only the author or a collaborator can update"),
        @ApiResponse(responseCode = "404", description = "Repository or issue not found")
    })
    @PatchMapping("/{number}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IssueResponse> updateIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @Valid @RequestBody UpdateIssueRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        IssueResponse issue = issueService.getIssue(repoId, number);
        IssueResponse response = issueService.updateIssue(user.getId(), issue.getId(), request);
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/issues/{number}/close
    // =========================================================================

    /**
     * Closes an open issue.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential issue number
     * @param authentication the current authentication
     * @return HTTP 200 with the closed issue
     */
    @Operation(summary = "Close an open issue")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Issue closed successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "403", description = "Only the author or a collaborator can close"),
        @ApiResponse(responseCode = "404", description = "Repository or issue not found")
    })
    @PostMapping("/{number}/close")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IssueResponse> closeIssue(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        IssueResponse issue = issueService.getIssue(repoId, number);
        IssueResponse response = issueService.closeIssue(user.getId(), issue.getId());
        return ResponseEntity.ok(response);
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/issues/{number}/comments
    // =========================================================================

    /**
     * Adds a comment to an issue.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential issue number
     * @param request        the comment request
     * @param authentication the current authentication
     * @return HTTP 201 with the saved comment
     */
    @Operation(summary = "Add a comment to an issue")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comment added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Repository or issue not found")
    })
    @PostMapping("/{number}/comments")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<IssueCommentResponse> addComment(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @Valid @RequestBody AddCommentRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        User user = extractUser(authentication);
        IssueResponse issue = issueService.getIssue(repoId, number);
        IssueCommentResponse response = issueService.addComment(user.getId(), issue.getId(), request.getBody());
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // =========================================================================
    // POST /api/repos/{owner}/{repo}/issues/{number}/labels
    // =========================================================================

    /**
     * Applies a label to an issue.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential issue number
     * @param request        the apply-label request containing the label ID
     * @param authentication the current authentication
     * @return HTTP 200 with no body
     */
    @Operation(summary = "Apply a label to an issue")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Label applied successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Repository, issue, or label not found"),
        @ApiResponse(responseCode = "422", description = "Label does not belong to this repository")
    })
    @PostMapping("/{number}/labels")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> applyLabel(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @Valid @RequestBody ApplyLabelRequest request,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        IssueResponse issue = issueService.getIssue(repoId, number);
        labelService.applyLabel(issue.getId(), request.getLabelId(), repoId);
        return ResponseEntity.ok().build();
    }

    // =========================================================================
    // DELETE /api/repos/{owner}/{repo}/issues/{number}/labels/{labelId}
    // =========================================================================

    /**
     * Removes a label from an issue.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param number         the sequential issue number
     * @param labelId        the label ID to remove
     * @param authentication the current authentication
     * @return HTTP 204 No Content
     */
    @Operation(summary = "Remove a label from an issue")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Label removed successfully"),
        @ApiResponse(responseCode = "401", description = "Authentication required"),
        @ApiResponse(responseCode = "404", description = "Repository, issue, or label not found")
    })
    @DeleteMapping("/{number}/labels/{labelId}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Void> removeLabel(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable Integer number,
            @PathVariable Long labelId,
            Authentication authentication) {

        Long repoId = resolveRepoId(owner, repo);
        IssueResponse issue = issueService.getIssue(repoId, number);
        labelService.removeLabel(issue.getId(), labelId);
        return ResponseEntity.noContent().build();
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
