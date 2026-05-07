package com.dvcs.webhook.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a new webhook.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookRequest {

    @NotBlank
    private String url;

    @NotBlank
    private String secret;

    @NotEmpty
    private List<String> events;
}
