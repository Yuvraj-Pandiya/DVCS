package com.dvcs.issue.service;

import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.ConflictException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.exception.InvalidRequestException;
import com.dvcs.issue.domain.IssueLabel;
import com.dvcs.issue.domain.Label;
import com.dvcs.issue.dto.LabelResponse;
import com.dvcs.issue.repository.IssueLabelRepository;
import com.dvcs.issue.repository.IssueRepository;
import com.dvcs.issue.repository.LabelRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Service for managing labels and their association with issues.
 *
 * <p>Labels are scoped to a repository. Only collaborators with WRITE or OWNER
 * role may create labels. Applying and removing labels from issues is idempotent.
 */
@Service
@Transactional
public class LabelService {

    private static final Logger log = LoggerFactory.getLogger(LabelService.class);

    private static final List<String> WRITE_ROLES = List.of("WRITE", "OWNER");

    private final LabelRepository labelRepository;
    private final IssueLabelRepository issueLabelRepository;
    private final IssueRepository issueRepository;
    private final CollaboratorRepository collaboratorRepository;

    public LabelService(LabelRepository labelRepository,
                        IssueLabelRepository issueLabelRepository,
                        IssueRepository issueRepository,
                        CollaboratorRepository collaboratorRepository) {
        this.labelRepository = labelRepository;
        this.issueLabelRepository = issueLabelRepository;
        this.issueRepository = issueRepository;
        this.collaboratorRepository = collaboratorRepository;
    }

    // =========================================================================
    // Public API
    // =========================================================================

    /**
     * Creates a new label in the given repository.
     *
     * <p>The requester must be a collaborator with WRITE or OWNER role.
     * Label names must be unique within a repository.
     *
     * @param repoId      the repository ID
     * @param name        the label name (max 64 characters)
     * @param color       the hex colour code, e.g. {@code #ff0000}
     * @param requesterId the ID of the user creating the label
     * @return the created label as a {@link LabelResponse}
     * @throws AccessDeniedException if the requester lacks WRITE or OWNER role
     * @throws ConflictException     if a label with the same name already exists in the repository
     */
    public LabelResponse createLabel(Long repoId, String name, String color, Long requesterId) {
        boolean hasWriteRole = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(repoId, requesterId, WRITE_ROLES);
        if (!hasWriteRole) {
            throw new AccessDeniedException(
                    "User " + requesterId + " is not authorized to create labels in repository " + repoId);
        }

        labelRepository.findByRepoIdAndName(repoId, name).ifPresent(existing -> {
            throw new ConflictException(
                    "Label '" + name + "' already exists in repository " + repoId);
        });

        Label label = Label.builder()
                .repoId(repoId)
                .name(name)
                .color(color)
                .build();

        Label saved = labelRepository.save(label);
        log.debug("Created label id={} '{}' in repo {}", saved.getId(), saved.getName(), repoId);
        return toResponse(saved);
    }

    /**
     * Applies a label to an issue (idempotent).
     *
     * <p>If the label is already applied to the issue, this method does nothing.
     * The label must belong to the same repository as the issue.
     *
     * @param issueId the issue ID
     * @param labelId the label ID
     * @param repoId  the repository ID (used to verify label ownership)
     * @throws EntityNotFoundException if the label or issue does not exist
     * @throws InvalidRequestException if the label does not belong to the given repository
     */
    public void applyLabel(Long issueId, Long labelId, Long repoId) {
        Label label = labelRepository.findById(labelId)
                .orElseThrow(() -> new EntityNotFoundException("Label not found: " + labelId));

        if (!label.getRepoId().equals(repoId)) {
            throw new InvalidRequestException(
                    "Label " + labelId + " does not belong to repository " + repoId);
        }

        if (!issueRepository.existsById(issueId)) {
            throw new EntityNotFoundException("Issue not found: " + issueId);
        }

        IssueLabel.IssueLabelId compositeId = IssueLabel.IssueLabelId.builder()
                .issueId(issueId)
                .labelId(labelId)
                .build();

        // Idempotent: only save if not already applied
        if (!issueLabelRepository.existsById(compositeId)) {
            IssueLabel issueLabel = IssueLabel.builder()
                    .id(compositeId)
                    .build();
            issueLabelRepository.save(issueLabel);
            log.debug("Applied label id={} to issue id={}", labelId, issueId);
        } else {
            log.debug("Label id={} already applied to issue id={}, skipping", labelId, issueId);
        }
    }

    /**
     * Removes a label from an issue.
     *
     * <p>If the label is not currently applied, this is a no-op.
     *
     * @param issueId the issue ID
     * @param labelId the label ID
     */
    public void removeLabel(Long issueId, Long labelId) {
        issueLabelRepository.deleteByIdIssueIdAndIdLabelId(issueId, labelId);
        log.debug("Removed label id={} from issue id={}", labelId, issueId);
    }

    /**
     * Lists all labels for a repository.
     *
     * @param repoId the repository ID
     * @return list of labels as {@link LabelResponse}
     */
    @Transactional(readOnly = true)
    public List<LabelResponse> listLabels(Long repoId) {
        return labelRepository.findByRepoId(repoId)
                .stream()
                .map(this::toResponse)
                .collect(Collectors.toList());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Maps a {@link Label} entity to a {@link LabelResponse} DTO.
     */
    private LabelResponse toResponse(Label label) {
        return LabelResponse.builder()
                .id(label.getId())
                .repoId(label.getRepoId())
                .name(label.getName())
                .color(label.getColor())
                .build();
    }
}
