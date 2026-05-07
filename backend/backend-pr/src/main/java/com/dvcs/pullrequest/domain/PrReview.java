package com.dvcs.pullrequest.domain;

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
 * JPA entity representing a pull request review, mapped to the {@code pr_reviews} table.
 *
 * <p>A review captures a reviewer's verdict (APPROVE, CHANGES_REQUESTED, or COMMENT)
 * on a pull request, along with an optional review body.
 */
@Entity
@Table(name = "pr_reviews")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_id", nullable = false)
    private Long prId;

    @Column(name = "reviewer_id", nullable = false)
    private Long reviewerId;

    /**
     * Review verdict: {@code APPROVE}, {@code CHANGES_REQUESTED}, or {@code COMMENT}.
     */
    @Column(name = "verdict", nullable = false, length = 24)
    private String verdict;

    /**
     * Optional review body text.
     */
    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "submitted_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime submittedAt;

    @PrePersist
    protected void onCreate() {
        if (submittedAt == null) {
            submittedAt = OffsetDateTime.now();
        }
    }
}
