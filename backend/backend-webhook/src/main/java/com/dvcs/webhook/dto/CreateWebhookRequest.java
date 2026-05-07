package com.dvcs.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for creating a new webhook.
 */
@Schema(description = "Request body for registering a new webhook on a repository")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateWebhookRequest {

    @Schema(description = "The URL to which webhook payloads will be delivered via HTTP POST",
            example = "https://ci.example.com/hooks/dvcs")
    @NotBlank
    private String url;

    @Schema(description = "Secret token used to sign webhook payloads (HMAC-SHA256); keep this value confidential",
            example = "my-super-secret-token-abc123")
    @NotBlank
    private String secret;

    @Schema(description = "List of event types that will trigger this webhook",
            example = "[\"push\", \"pull_request\", \"issues\"]")
    @NotEmpty
    private List<String> events;
}
