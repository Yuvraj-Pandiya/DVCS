package com.dvcs.issue.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * DTO returned from the issue service representing a single issue.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueResponse {

    private Long id;
    private Integer number;
    private String title;
    private String body;
    private Long authorId;
    private String status;
    private OffsetDateTime createdAt;
}
