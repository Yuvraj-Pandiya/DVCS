package com.dvcs.pullrequest.dto;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * DTO for pull request list items.
 *
 * <p>Enriches the basic PR entity with author username, labels, and review status
 * for efficient list rendering in the frontend.
 */
public record PrListItemDto(
        Long id,
        Integer number,
        String title,
        String body,
        String headBranch,
        String baseBranch,
        Long authorId,
        String authorUsername,
        String status,
        OffsetDateTime mergedAt,
        OffsetDateTime createdAt,
        List<LabelDto> labels,
        ReviewStatusDto reviewStatus
) {
    /**
     * Label DTO for PR list items.
     */
    public record LabelDto(
            Long id,
            String name,
            String color
    ) {}

    /**
     * Review status summary for PR list items.
     */
    public record ReviewStatusDto(
            int approveCount,
            int changesRequestedCount,
            int commentCount,
            boolean hasApproval,
            boolean hasChangesRequested
    ) {}
}
