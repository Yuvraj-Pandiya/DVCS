package com.dvcs.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Response DTO for webhook data.
 *
 * <p>NOTE: The {@code secret} field is intentionally excluded from this response
 * for security reasons (Requirement 12.5 / 18.5).
 */
@Schema(description = "Webhook configuration data returned by the webhook API (secret is intentionally excluded)")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    @Schema(description = "Unique identifier of the webhook", example = "9")
    private Long id;

    @Schema(description = "ID of the repository this webhook belongs to", example = "1")
    private Long repoId;

    @Schema(description = "The URL to which webhook payloads are delivered", example = "https://ci.example.com/hooks/dvcs")
    private String url;

    @Schema(description = "List of event types that trigger this webhook", example = "[\"push\", \"pull_request\", \"issues\"]")
    private List<String> events;

    @Schema(description = "Whether this webhook is currently active and will receive deliveries", example = "true")
    private boolean active;

    @Schema(description = "Timestamp when the webhook was created", example = "2026-01-15T10:30:00Z")
    private OffsetDateTime createdAt;
}
