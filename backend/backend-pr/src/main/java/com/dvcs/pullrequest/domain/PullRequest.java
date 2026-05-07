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
 * JPA entity representing a pull request, mapped to the {@code pull_requests} table.
 *
 * <p>A pull request represents a request to merge changes from a head branch
 * into a base branch within a repository.
 */
@Entity
@Table(name = "pull_requests")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PullRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "number", nullable = false)
    private Integer number;

    @Column(name = "title", nullable = false, length = 512)
    private String title;

    @Column(name = "body", columnDefinition = "TEXT")
    private String body;

    @Column(name = "head_branch", nullable = false, length = 255)
    private String headBranch;

    @Column(name = "base_branch", nullable = false, length = 255)
    private String baseBranch;

    @Column(name = "author_id", nullable = false)
    private Long authorId;

    /**
     * Status of the pull request: {@code open}, {@code closed}, or {@code merged}.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "merged_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime mergedAt;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "open";
        }
    }
}
