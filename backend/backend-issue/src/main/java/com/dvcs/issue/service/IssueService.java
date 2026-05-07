package com.dvcs.issue.service;

import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.validation.InputSanitizer;
import com.dvcs.issue.domain.Issue;
import com.dvcs.issue.domain.IssueComment;
import com.dvcs.issue.dto.CreateIssueRequest;
import com.dvcs.issue.dto.IssueCommentResponse;
import com.dvcs.issue.dto.IssueResponse;
import com.dvcs.issue.dto.UpdateIssueRequest;
import com.dvcs.issue.event.IssueClosedEvent;
import com.dvcs.issue.event.IssueCommentCreatedEvent;
import com.dvcs.issue.repository.IssueCommentRepository;
import com.dvcs.issue.repository.IssueRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing issue lifecycle operations.
 *
 * <p>Handles creating issues, listing, retrieving, updating, closing, and commenting.
 *
 * <p>Cross-module side effects (webhook delivery on close, notifications on comment)
 * are published as Spring application events via {@link ApplicationEventPublisher}.
 * This decouples the issue module from {@code backend-webhook} and
 * {@code backend-notification}, which are not declared as dependencies of this module.
 * When those modules are implemented, they should register
 * {@link org.springframework.context.event.EventListener} beans for
 * {@link IssueClosedEvent} and {@link IssueCommentCreatedEvent}.
 */
@Service
@Transactional
public class IssueService {

    private static final Logger log = LoggerFactory.getLogger(IssueService.class);

    private static final List<String> WRITE_ROLES = List.of("WRITE", "OWNER");

    private final IssueRepository issueRepository;
    private final IssueCommentRepository issueCommentRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final InputSanitizer inputSanitizer;

    public IssueService(IssueRepository issueRepository,
                        IssueCommentRepository issueCommentRepository,
                        CollaboratorRepository collaboratorRepository,
                        ApplicationEventPublisher eventPublisher,
                        InputSanitizer inputSanitizer) {
        this.issueRepository = issueRepository;
        this.issueCommentRepository = issueCommentRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.eventPublisher = eventPublisher;
        this.inputSanitizer = inputSanitizer;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Creates a new issue in the given repository.
     *
     * <p>Assigns a sequential issue number scoped to the repository and sets
     * the initial status to {@code open}.
     *
     * @param userId the ID of the user creating the issue
     * @param repoId the repository ID
     * @param req    the creation request containing title and optional body
     * @return the saved issue as an {@link IssueResponse}
     */
    public IssueResponse createIssue(Long userId, Long repoId, CreateIssueRequest req) {
        int nextNumber = issueRepository.findNextNumber(repoId);

        Issue issue = Issue.builder()
                .repoId(repoId)
                .number(nextNumber)
                .title(req.getTitle())
                .body(inputSanitizer.sanitize(req.getBody()))
                .authorId(userId)
                .status("open")
                .build();

        Issue saved = issueRepository.save(issue);
        log.debug("Created issue #{} (id={}) in repo {}", saved.getNumber(), saved.getId(), repoId);
        return toResponse(saved);
    }

    /**
     * Lists issues for a repository, optionally filtered by status.
     *
     * @param repoId   the repository ID
     * @param status   the status filter ({@code open} or {@code closed}); if null or blank, all issues are returned
     * @param pageable pagination parameters
     * @return page of issues as {@link IssueResponse}
     */
    @Transactional(readOnly = true)
    public Page<IssueResponse> listIssues(Long repoId, String status, Pageable pageable) {
        if (StringUtils.hasText(status)) {
            return issueRepository.findByRepoIdAndStatus(repoId, status, pageable)
                    .map(this::toResponse);
        }
        return issueRepository.findByRepoId(repoId, pageable)
                .map(this::toResponse);
    }

    /**
     * Retrieves a single issue by its repository-scoped number.
     *
     * @param repoId the repository ID
     * @param number the sequential issue number within the repository
     * @return the issue as an {@link IssueResponse}
     * @throws EntityNotFoundException if no issue with that number exists in the repository
     */
    @Transactional(readOnly = true)
    public IssueResponse getIssue(Long repoId, Integer number) {
        Issue issue = issueRepository.findByRepoIdAndNumber(repoId, number)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Issue #" + number + " not found in repository " + repoId));
        return toResponse(issue);
    }

    /**
     * Updates the title and/or body of an issue.
     *
     * <p>Only non-null fields in the request are applied. The caller must be
     * the issue author or a collaborator with WRITE or OWNER role.
     *
     * @param userId  the ID of the requesting user
     * @param issueId the issue ID
     * @param req     the update request
     * @return the updated issue as an {@link IssueResponse}
     * @throws EntityNotFoundException if the issue does not exist
     * @throws AccessDeniedException   if the user is not authorized to update the issue
     */
    public IssueResponse updateIssue(Long userId, Long issueId, UpdateIssueRequest req) {
        Issue issue = loadIssueById(issueId);
        checkWriteAccess(userId, issue);

        if (req.getTitle() != null) {
            issue.setTitle(req.getTitle());
        }
        if (req.getBody() != null) {
            issue.setBody(inputSanitizer.sanitize(req.getBody()));
        }

        Issue saved = issueRepository.save(issue);
        log.debug("Updated issue id={} by user {}", issueId, userId);
        return toResponse(saved);
    }

    /**
     * Closes an open issue.
     *
     * <p>The caller must be the issue author or a collaborator with WRITE or OWNER role.
     * After closing, an {@link IssueClosedEvent} is published so that the webhook
     * module can deliver an {@code issues} event to registered subscribers.
     *
     * @param userId  the ID of the requesting user
     * @param issueId the issue ID
     * @return the closed issue as an {@link IssueResponse}
     * @throws EntityNotFoundException if the issue does not exist
     * @throws AccessDeniedException   if the user is not authorized to close the issue
     */
    public IssueResponse closeIssue(Long userId, Long issueId) {
        Issue issue = loadIssueById(issueId);
        checkWriteAccess(userId, issue);

        issue.setStatus("closed");
        Issue saved = issueRepository.save(issue);
        log.debug("Closed issue id={} by user {}", issueId, userId);

        // Publish event for webhook delivery.
        // The backend-webhook module is not a dependency of this module; cross-module
        // communication is handled via Spring application events.
        // When WebhookDeliveryService is implemented, register an @EventListener for IssueClosedEvent
        // and call: webhookDeliveryService.deliverAsync(event.repoId(), "issues", event.payload())
        String payload = buildIssueClosedPayload(saved);
        eventPublisher.publishEvent(new IssueClosedEvent(saved.getRepoId(), saved.getId(), payload));

        return toResponse(saved);
    }

    /**
     * Adds a comment to an issue.
     *
     * <p>After saving the comment, an {@link IssueCommentCreatedEvent} is published
     * so that the notification module can notify the issue author and all previous
     * commenters (excluding the commenter themselves).
     *
     * @param userId  the ID of the user posting the comment
     * @param issueId the issue ID
     * @param body    the comment text
     * @return the saved comment as an {@link IssueCommentResponse}
     * @throws EntityNotFoundException if the issue does not exist
     */
    public IssueCommentResponse addComment(Long userId, Long issueId, String body) {
        Issue issue = loadIssueById(issueId);

        IssueComment comment = IssueComment.builder()
                .issueId(issueId)
                .body(inputSanitizer.sanitize(body))
                .authorId(userId)
                .build();

        IssueComment saved = issueCommentRepository.save(comment);
        log.debug("Added comment id={} to issue id={} by user {}", saved.getId(), issueId, userId);

        // Collect users to notify: issue author + all prior commenters, excluding the commenter.
        // The backend-notification module is not a dependency of this module; cross-module
        // communication is handled via Spring application events.
        // When NotificationService is implemented, register an @EventListener for IssueCommentCreatedEvent
        // and call: notificationService.createNotification(targetUserId, "issue", issueId, "comment")
        List<Long> targetUserIds = collectNotificationTargets(userId, issue, issueId);
        if (!targetUserIds.isEmpty()) {
            eventPublisher.publishEvent(
                    new IssueCommentCreatedEvent(issueId, saved.getId(), userId, targetUserIds));
        }

        return toCommentResponse(saved);
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Loads an issue by its primary key, throwing {@link EntityNotFoundException} if absent.
     */
    private Issue loadIssueById(Long issueId) {
        return issueRepository.findById(issueId)
                .orElseThrow(() -> new EntityNotFoundException("Issue not found: " + issueId));
    }

    /**
     * Checks that the given user is either the issue author or a collaborator
     * with WRITE or OWNER role on the issue's repository.
     *
     * @throws AccessDeniedException if the user has neither the author relationship nor a write role
     */
    private void checkWriteAccess(Long userId, Issue issue) {
        if (issue.getAuthorId().equals(userId)) {
            return;
        }
        boolean hasWriteRole = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(issue.getRepoId(), userId, WRITE_ROLES);
        if (!hasWriteRole) {
            throw new AccessDeniedException(
                    "User " + userId + " is not authorized to modify issue " + issue.getId());
        }
    }

    /**
     * Collects the distinct user IDs that should be notified about a new comment.
     * Includes the issue author and all prior commenters, excluding the commenter.
     */
    private List<Long> collectNotificationTargets(Long commenterId, Issue issue, Long issueId) {
        List<Long> priorCommenterIds = issueCommentRepository
                .findByIssueIdOrderByCreatedAtAsc(issueId)
                .stream()
                .map(IssueComment::getAuthorId)
                .filter(id -> !id.equals(commenterId))
                .distinct()
                .collect(Collectors.toList());

        // Add issue author if different from commenter and not already in the list
        Long authorId = issue.getAuthorId();
        if (!authorId.equals(commenterId) && !priorCommenterIds.contains(authorId)) {
            priorCommenterIds.add(0, authorId);
        }

        return priorCommenterIds;
    }

    /**
     * Builds a simple JSON-like payload string for the issue-closed webhook event.
     */
    private String buildIssueClosedPayload(Issue issue) {
        return "{\"action\":\"closed\",\"issue_id\":" + issue.getId()
                + ",\"repo_id\":" + issue.getRepoId()
                + ",\"number\":" + issue.getNumber()
                + ",\"title\":\"" + escapeJson(issue.getTitle()) + "\"}";
    }

    private String escapeJson(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    /**
     * Maps an {@link Issue} entity to an {@link IssueResponse} DTO.
     */
    private IssueResponse toResponse(Issue issue) {
        return IssueResponse.builder()
                .id(issue.getId())
                .number(issue.getNumber())
                .title(issue.getTitle())
                .body(issue.getBody())
                .authorId(issue.getAuthorId())
                .status(issue.getStatus())
                .createdAt(issue.getCreatedAt())
                .build();
    }

    /**
     * Maps an {@link IssueComment} entity to an {@link IssueCommentResponse} DTO.
     */
    private IssueCommentResponse toCommentResponse(IssueComment comment) {
        return IssueCommentResponse.builder()
                .id(comment.getId())
                .issueId(comment.getIssueId())
                .body(comment.getBody())
                .authorId(comment.getAuthorId())
                .createdAt(comment.getCreatedAt())
                .build();
    }
}
