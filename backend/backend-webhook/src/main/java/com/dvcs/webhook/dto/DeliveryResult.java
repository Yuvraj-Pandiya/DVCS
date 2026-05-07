package com.dvcs.webhook.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a webhook delivery attempt.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DeliveryResult {

    /** Whether the delivery ultimately succeeded (2xx response). */
    private boolean success;

    /** The final HTTP status code received (0 if no response was received). */
    private int statusCode;

    /** Total number of delivery attempts made (1–3). */
    private int attempts;

    /** Error message if delivery failed, or null on success. */
    private String errorMessage;
}
