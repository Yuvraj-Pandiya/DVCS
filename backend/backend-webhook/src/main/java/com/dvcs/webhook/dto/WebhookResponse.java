package com.dvcs.webhook.dto;

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
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookResponse {

    private Long id;
    private Long repoId;
    private String url;
    private List<String> events;
    private boolean active;
    private OffsetDateTime createdAt;
}
