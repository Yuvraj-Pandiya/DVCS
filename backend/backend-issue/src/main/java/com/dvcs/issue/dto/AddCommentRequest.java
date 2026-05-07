package com.dvcs.issue.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for adding a comment to an issue.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {

    @NotBlank
    private String body;
}
