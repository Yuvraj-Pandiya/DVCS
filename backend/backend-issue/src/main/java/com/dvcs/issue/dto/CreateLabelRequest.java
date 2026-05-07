package com.dvcs.issue.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a new label in a repository.
 */
@Schema(description = "Request body for creating a new label in a repository")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLabelRequest {

    @Schema(description = "Label name; must be unique within the repository", example = "bug")
    @NotBlank
    @Size(max = 64)
    private String name;

    @Schema(description = "Label color as a CSS hex color code (e.g. #d73a4a)", example = "#d73a4a")
    @NotBlank
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    private String color;
}
