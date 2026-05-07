package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO returned from the issue service representing a comment on an issue.
 */
@Schema(description = "Issue comment data returned by the issue API")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCommentResponse {

    @Schema(description = "Unique identifier of the comment", example = "88")
    private Long id;

    @Schema(description = "ID of the issue this comment belongs to", example = "23")
    private Long issueId;

    @Schema(description = "Comment body text (may contain Markdown)",
            example = "I can reproduce this on iOS 17.2. The button click event fires but the form submission is blocked.")
    private String body;

    @Schema(description = "ID of the user who wrote the comment", example = "5")
    private Long authorId;

    @Schema(description = "Timestamp when the comment was created", example = "2026-03-21T09:15:00Z")
    private OffsetDateTime createdAt;
}
