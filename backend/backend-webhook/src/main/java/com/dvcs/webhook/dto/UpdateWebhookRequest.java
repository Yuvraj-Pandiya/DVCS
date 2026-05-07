package com.dvcs.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Request DTO for updating an existing webhook.
 *
 * <p>All fields are nullable; only non-null fields are applied.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class UpdateWebhookRequest {

    /** New URL for the webhook, or null to leave unchanged. */
    private String url;

    /** New event list for the webhook, or null to leave unchanged. */
    private List<String> events;

    /** New active flag for the webhook, or null to leave unchanged. */
    private Boolean active;
}
