package com.dvcs.notification.domain;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA entity representing an in-app notification, mapped to the {@code notifications} table.
 *
 * <p>Notifications are created when events involving a user occur (PR review, issue comment,
 * mention, pipeline completion). They are delivered in real time via WebSocket/STOMP and
 * can be retrieved and marked as read via the REST API.
 */
@Schema(description = "An in-app notification delivered to a user when a relevant event occurs (PR review, issue comment, pipeline completion, etc.)")
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Schema(description = "Unique identifier of the notification", example = "101")
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who should receive this notification. */
    @Schema(description = "ID of the user who should receive this notification", example = "5")
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The type of subject that triggered the notification (e.g., "pull_request", "issue"). */
    @Schema(description = "Type of the subject that triggered the notification",
            example = "pull_request",
            allowableValues = {"pull_request", "issue", "pipeline_run"})
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    /** The ID of the subject entity (e.g., PR ID, issue ID). */
    @Schema(description = "ID of the subject entity (e.g. PR ID, issue ID)", example = "15")
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** The reason for the notification (e.g., "review_approve", "issue_comment"). */
    @Schema(description = "Reason for the notification",
            example = "review_approve",
            allowableValues = {"review_approve", "review_changes_requested", "issue_comment", "pr_comment", "pipeline_complete"})
    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    /** Whether the user has read this notification. */
    @Schema(description = "Whether the user has read this notification", example = "false")
    @Column(name = "read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /** Timestamp when the notification was created. */
    @Schema(description = "Timestamp when the notification was created", example = "2026-03-20T14:45:00Z")
    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
    }
}
