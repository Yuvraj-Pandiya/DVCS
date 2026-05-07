package com.dvcs.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a new issue.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateIssueRequest {

    @NotBlank
    @Size(max = 512)
    private String title;

    private String body;
}
