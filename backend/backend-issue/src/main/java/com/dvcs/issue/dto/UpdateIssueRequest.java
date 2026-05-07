package com.dvcs.issue.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for updating an existing issue.
 * Only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateIssueRequest {

    @Size(max = 512)
    private String title;

    private String body;
}
