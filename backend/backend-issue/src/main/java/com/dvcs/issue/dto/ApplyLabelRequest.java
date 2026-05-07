package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for applying a label to an issue.
 */
@Schema(description = "Request body for applying an existing label to an issue")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyLabelRequest {

    @Schema(description = "ID of the label to apply; the label must belong to the same repository as the issue",
            example = "3")
    @NotNull
    private Long labelId;
}
