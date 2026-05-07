package com.dvcs.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing webhook.
 *
 * <p>All fields are nullable; only non-null fields are applied.
 */
@Schema(description = "Request body for updating an existing webhook; only non-null fields are applied")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWebhookRequest {

    @Schema(description = "New URL for the webhook; leave null to keep the existing URL",
            example = "https://ci.example.com/hooks/dvcs-v2")
    /** New URL for the webhook, or null to leave unchanged. */
    private String url;

    @Schema(description = "New list of event types for the webhook; leave null to keep the existing events",
            example = "[\"push\", \"pull_request\"]")
    /** New event list for the webhook, or null to leave unchanged. */
    private List<String> events;

    @Schema(description = "New active flag for the webhook; set to false to temporarily disable delivery; leave null to keep unchanged",
            example = "true")
    /** New active flag for the webhook, or null to leave unchanged. */
    private Boolean active;
}
