package com.dvcs.issue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body for creating a new label in a repository.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateLabelRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    @NotBlank
    @Pattern(regexp = "#[0-9a-fA-F]{6}")
    private String color;
}
