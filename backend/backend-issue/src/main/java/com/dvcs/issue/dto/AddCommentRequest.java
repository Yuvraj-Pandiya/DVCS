package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for adding a comment to an issue.
 */
@Schema(description = "Request body for adding a comment to an issue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AddCommentRequest {

    @Schema(description = "Comment body text (supports Markdown)",
            example = "I can reproduce this on iOS 17.2. The button click event fires but the form submission is blocked.")
    @NotBlank
    private String body;
}
