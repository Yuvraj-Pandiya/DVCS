package com.dvcs.webhook.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a webhook delivery attempt.
 */
@Schema(description = "Result of a webhook delivery attempt, including retry information")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {

    @Schema(description = "Whether the delivery ultimately succeeded (received a 2xx HTTP response)", example = "true")
    /** Whether the delivery ultimately succeeded (2xx response). */
    private boolean success;

    @Schema(description = "The final HTTP status code received from the webhook endpoint; 0 if no response was received",
            example = "200")
    /** The final HTTP status code received (0 if no response was received). */
    private int statusCode;

    @Schema(description = "Total number of delivery attempts made (1 to 3, with exponential backoff on failure)",
            example = "1")
    /** Total number of delivery attempts made (1–3). */
    private int attempts;

    @Schema(description = "Error message if delivery failed; null on success",
            example = "Connection refused: ci.example.com:443")
    /** Error message if delivery failed, or null on success. */
    private String errorMessage;
}
