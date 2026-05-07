package com.dvcs.webhook.service;

import com.dvcs.webhook.domain.Webhook;
import com.dvcs.webhook.dto.DeliveryResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Service responsible for delivering webhook payloads to registered URLs.
 *
 * <p>Payloads are signed with HMAC-SHA256 using the webhook secret.
 * Failed deliveries are retried with exponential backoff (1s, 2s, 4s) for up to 3 attempts.
 *
 * <p>SECURITY: The webhook secret is NEVER logged. Only the webhook ID and URL are logged.
 */
@Service
public class WebhookDeliveryService {

    private static final Logger log = LoggerFactory.getLogger(WebhookDeliveryService.class);

    private static final int MAX_ATTEMPTS = 3;
    private static final int[] BACKOFF_MS = {1000, 2000, 4000};

    private final ObjectMapper objectMapper;

    public WebhookDeliveryService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // =========================================================================
    // Async delivery
    // =========================================================================

    /**
     * Asynchronously delivers a webhook payload to the configured URL.
     *
     * <p>Runs in a separate thread pool thread (Spring {@code @Async}).
     *
     * @param webhook   the webhook configuration (URL, secret, etc.)
     * @param eventType the event type (e.g., "push", "issues", "ping")
     * @param payload   the payload object to serialize as JSON
     * @return a {@link CompletableFuture} containing the delivery result
     */
    @Async
    public CompletableFuture<DeliveryResult> deliver(Webhook webhook, String eventType,
                                                      Object payload) {
        DeliveryResult result = deliverSync(webhook, eventType, payload);
        return CompletableFuture.completedFuture(result);
    }

    // =========================================================================
    // Synchronous delivery (used by test endpoint and internally)
    // =========================================================================

    /**
     * Synchronously delivers a webhook payload to the configured URL.
     *
     * <p>Retries with exponential backoff on non-2xx responses or exceptions.
     *
     * @param webhook   the webhook configuration
     * @param eventType the event type
     * @param payload   the payload object to serialize as JSON
     * @return the delivery result
     */
    public DeliveryResult deliverSync(Webhook webhook, String eventType, Object payload) {
        byte[] jsonBytes;
        try {
            jsonBytes = objectMapper.writeValueAsBytes(payload);
        } catch (Exception e) {
            log.error("Failed to serialize webhook payload for webhook id={}: {}",
                    webhook.getId(), e.getMessage());
            return DeliveryResult.builder()
                    .success(false)
                    .statusCode(0)
                    .attempts(0)
                    .errorMessage("Payload serialization failed: " + e.getMessage())
                    .build();
        }

        String signature = computeHmacSignature(webhook.getSecret(), jsonBytes);

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(webhook.getUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .header("X-Hub-Signature-256", signature)
                .header("X-GitHub-Event", eventType)
                .header("X-Delivery", UUID.randomUUID().toString())
                .POST(HttpRequest.BodyPublishers.ofByteArray(jsonBytes))
                .build();

        int attempts = 0;
        int lastStatus = 0;
        Exception lastException = null;

        while (attempts < MAX_ATTEMPTS) {
            attempts++;
            try {
                HttpResponse<String> response = client.send(request,
                        HttpResponse.BodyHandlers.ofString());
                lastStatus = response.statusCode();
                if (lastStatus >= 200 && lastStatus < 300) {
                    log.debug("Webhook delivery succeeded for webhook id={} url={} attempt={}",
                            webhook.getId(), webhook.getUrl(), attempts);
                    return DeliveryResult.builder()
                            .success(true)
                            .statusCode(lastStatus)
                            .attempts(attempts)
                            .build();
                }
                log.warn("Webhook delivery attempt {} returned non-2xx status {} for webhook id={} url={}",
                        attempts, lastStatus, webhook.getId(), webhook.getUrl());
            } catch (IOException | InterruptedException e) {
                lastException = e;
                log.warn("Webhook delivery attempt {} failed for webhook id={}: {}",
                        attempts, webhook.getId(), e.getMessage());
                if (e instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
            }

            if (attempts < MAX_ATTEMPTS) {
                try {
                    Thread.sleep(BACKOFF_MS[attempts - 1]);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    log.warn("Webhook delivery backoff interrupted for webhook id={}", webhook.getId());
                    break;
                }
            }
        }

        String errorMessage = lastException != null
                ? lastException.getMessage()
                : "HTTP " + lastStatus;

        log.warn("Webhook delivery failed after {} attempts for webhook id={} url={}",
                attempts, webhook.getId(), webhook.getUrl());

        return DeliveryResult.builder()
                .success(false)
                .statusCode(lastStatus)
                .attempts(attempts)
                .errorMessage(errorMessage)
                .build();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Computes the HMAC-SHA256 signature for the given payload bytes.
     *
     * <p>SECURITY: The secret is used only for HMAC computation and is never logged.
     *
     * @param secret    the webhook secret
     * @param jsonBytes the serialized JSON payload
     * @return the signature in the format {@code sha256={hex}}
     */
    private String computeHmacSignature(String secret, byte[] jsonBytes) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(
                    secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(keySpec);
            byte[] hmacBytes = mac.doFinal(jsonBytes);
            return "sha256=" + HexFormat.of().formatHex(hmacBytes);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 signature", e);
        }
    }
}
