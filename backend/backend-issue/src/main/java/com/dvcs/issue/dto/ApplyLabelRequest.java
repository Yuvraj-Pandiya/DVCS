package com.dvcs.issue.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for applying a label to an issue.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApplyLabelRequest {

    @NotNull
    private Long labelId;
}
