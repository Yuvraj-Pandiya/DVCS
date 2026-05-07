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
 * JPA entity representing an inline comment on a pull request,
 * mapped to the {@code pr_comments} table.
 *
 * <p>Comments may be associated with a specific review, file path, and line number
 * for inline code review, or may be general PR-level comments.
 */
@Entity
@Table(name = "pr_comments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PrComment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "pr_id", nullable = false)
    private Long prId;

    /**
     * Optional reference to the review this comment belongs to.
     */
    @Column(name = "review_id")
    private Long reviewId;

    /**
     * Optional file path for inline comments.
     */
    @Column(name = "file_path", length = 512)
    private String filePath;

    /**
     * Optional line number for inline comments.
     */
    @Column(name = "line_number")
    private Integer lineNumber;

    @Column(name = "body", nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

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
