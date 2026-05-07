package com.dvcs.issue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO returned from the issue service representing a comment on an issue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueCommentResponse {

    private Long id;
    private Long issueId;
    private String body;
    private Long authorId;
    private OffsetDateTime createdAt;
}
