package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO returned when a label is created or listed.
 */
@Schema(description = "Label data returned when a label is created or listed")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LabelResponse {

    @Schema(description = "Unique identifier of the label", example = "3")
    private Long id;

    @Schema(description = "ID of the repository this label belongs to", example = "1")
    private Long repoId;

    @Schema(description = "Label name", example = "bug")
    private String name;

    @Schema(description = "Label color as a CSS hex color code", example = "#d73a4a")
    private String color;
}
