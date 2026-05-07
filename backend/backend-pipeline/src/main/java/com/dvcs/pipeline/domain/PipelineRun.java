package com.dvcs.pipeline.domain;

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
 * JPA entity representing a CI/CD pipeline run, mapped to the {@code pipeline_runs} table.
 *
 * <p>A pipeline run is created when a push event is received. It tracks the overall
 * status and per-stage results as a JSONB column.
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
@Entity
@Table(name = "pipeline_runs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PipelineRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "commit_sha", nullable = false, length = 64)
    private String commitSha;

    /**
     * Pipeline status: {@code PENDING}, {@code RUNNING}, {@code SUCCESS}, or {@code FAILURE}.
     */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /**
     * JSONB column storing per-stage results as a JSON string.
     *
     * <p>Structure:
     * <pre>
     * {
     *   "stages": [
     *     { "name": "build", "status": "SUCCESS", "startedAt": "...", "finishedAt": "...", "log": "..." },
     *     { "name": "test",  "status": "SUCCESS", "startedAt": "...", "finishedAt": "...", "log": "..." }
     *   ]
     * }
     * </pre>
     */
    @Column(name = "stages_json", columnDefinition = "JSONB")
    private String stagesJson;

    @Column(name = "started_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime startedAt;

    @Column(name = "finished_at", columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime finishedAt;

    @Column(name = "created_at", nullable = false, updatable = false,
            columnDefinition = "TIMESTAMPTZ")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = OffsetDateTime.now();
        }
        if (status == null) {
            status = "PENDING";
        }
    }
}
