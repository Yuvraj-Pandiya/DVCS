package com.dvcs.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * JPA entity representing commit metadata, mapped to the {@code commits_meta} table.
 */
@Entity
@Table(name = "commits_meta")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CommitMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "sha", nullable = false, length = 64)
    private String sha;

    @Column(name = "author_id")
    private Long authorId;

    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(name = "authored_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime authoredAt;

    @Column(name = "committed_at", nullable = false, columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime committedAt;
}
