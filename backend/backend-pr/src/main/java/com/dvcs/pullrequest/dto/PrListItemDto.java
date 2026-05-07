package com.dvcs.pullrequest.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO for pull request list items.
 *
 * <p>Enriches the basic PR entity with author username, labels, and review status
 * for efficient list rendering in the frontend.
 */
@Schema(description = "Pull request summary item returned in paginated list responses")
public record PrListItemDto(
        @Schema(description = "Unique identifier of the pull request", example = "15")
        Long id,

        @Schema(description = "Sequential pull request number within the repository", example = "3")
        Integer number,

        @Schema(description = "Pull request title", example = "feat: add OAuth2 login support")
        String title,

        @Schema(description = "Pull request description body", example = "This PR adds OAuth2 login via GitHub and Google.")
        String body,

        @Schema(description = "Source branch containing the changes", example = "feature/add-oauth")
        String headBranch,

        @Schema(description = "Target branch to merge into", example = "main")
        String baseBranch,

        @Schema(description = "ID of the user who opened the pull request", example = "5")
        Long authorId,

        @Schema(description = "Username of the user who opened the pull request", example = "alice")
        String authorUsername,

        @Schema(description = "Current status of the pull request", example = "open",
                allowableValues = {"open", "closed", "merged"})
        String status,

        @Schema(description = "Timestamp when the pull request was merged; null if not yet merged",
                example = "2026-04-01T09:00:00Z")
        OffsetDateTime mergedAt,

        @Schema(description = "Timestamp when the pull request was created", example = "2026-03-20T14:45:00Z")
        OffsetDateTime createdAt,

        @Schema(description = "Labels applied to this pull request")
        List<LabelDto> labels,

        @Schema(description = "Summary of review activity on this pull request")
        ReviewStatusDto reviewStatus
) {
    /**
     * Label DTO for PR list items.
     */
    @Schema(description = "Label applied to a pull request")
    public record LabelDto(
            @Schema(description = "Unique identifier of the label", example = "3")
            Long id,

            @Schema(description = "Label name", example = "enhancement")
            String name,

            @Schema(description = "Label color as a hex color code", example = "#84b6eb")
            String color
    ) {}

    /**
     * Review status summary for PR list items.
     */
    @Schema(description = "Summary of review verdicts on a pull request")
    public record ReviewStatusDto(
            @Schema(description = "Number of APPROVE reviews", example = "2")
            int approveCount,

            @Schema(description = "Number of CHANGES_REQUESTED reviews", example = "0")
            int changesRequestedCount,

            @Schema(description = "Number of COMMENT reviews", example = "1")
            int commentCount,

            @Schema(description = "Whether at least one reviewer has approved", example = "true")
            boolean hasApproval,

            @Schema(description = "Whether any reviewer has requested changes", example = "false")
            boolean hasChangesRequested
    ) {}
}
