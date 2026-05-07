package com.dvcs.pipeline.domain;

import java.time.OffsetDateTime;

/**
 * Represents a single stage within a pipeline run (e.g., "build" or "test").
 *
 * <p>Instances are serialized to JSON and stored in the {@code stages_json} JSONB column
 * of the {@code pipeline_runs} table.
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
public class PipelineStage {

    private String name;
    private String status;
    private OffsetDateTime startedAt;
    private OffsetDateTime finishedAt;
    private String log;

    public PipelineStage() {}

    public PipelineStage(String name, String status,
                         OffsetDateTime startedAt, OffsetDateTime finishedAt,
                         String log) {
        this.name = name;
        this.status = status;
        this.startedAt = startedAt;
        this.finishedAt = finishedAt;
        this.log = log;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public OffsetDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(OffsetDateTime startedAt) { this.startedAt = startedAt; }

    public OffsetDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(OffsetDateTime finishedAt) { this.finishedAt = finishedAt; }

    public String getLog() { return log; }
    public void setLog(String log) { this.log = log; }
}
