package com.dvcs.notification.domain;

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
@Entity
@Table(name = "notifications")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The user who should receive this notification. */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** The type of subject that triggered the notification (e.g., "pull_request", "issue"). */
    @Column(name = "subject_type", nullable = false, length = 32)
    private String subjectType;

    /** The ID of the subject entity (e.g., PR ID, issue ID). */
    @Column(name = "subject_id", nullable = false)
    private Long subjectId;

    /** The reason for the notification (e.g., "review_approve", "issue_comment"). */
    @Column(name = "reason", nullable = false, length = 64)
    private String reason;

    /** Whether the user has read this notification. */
    @Column(name = "read", nullable = false)
    @Builder.Default
    private boolean read = false;

    /** Timestamp when the notification was created. */
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
