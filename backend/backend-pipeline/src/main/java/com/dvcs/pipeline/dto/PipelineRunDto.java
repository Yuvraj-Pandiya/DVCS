package com.dvcs.pipeline.dto;

import com.dvcs.pipeline.domain.PipelineRun;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.OffsetDateTime;

/**
 * DTO for a pipeline run summary (used in paginated list responses).
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 *
 * @param id         the pipeline run ID
 * @param repoId     the repository ID
 * @param commitSha  the commit SHA that triggered the run
 * @param status     the run status (PENDING, RUNNING, SUCCESS, FAILURE)
 * @param startedAt  when the run started (null if still PENDING)
 * @param finishedAt when the run finished (null if not yet complete)
 * @param createdAt  when the run record was created
 */
@Schema(description = "Pipeline run summary returned in paginated list responses")
public record PipelineRunDto(
        @Schema(description = "Unique identifier of the pipeline run", example = "55")
        Long id,

        @Schema(description = "ID of the repository that triggered this pipeline run", example = "1")
        Long repoId,

        @Schema(description = "SHA of the commit that triggered this pipeline run",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        String commitSha,

        @Schema(description = "Current status of the pipeline run", example = "SUCCESS",
                allowableValues = {"PENDING", "RUNNING", "SUCCESS", "FAILURE"})
        String status,

        @Schema(description = "Timestamp when the pipeline run started; null if still PENDING",
                example = "2026-03-20T14:45:00Z")
        OffsetDateTime startedAt,

        @Schema(description = "Timestamp when the pipeline run finished; null if not yet complete",
                example = "2026-03-20T14:47:30Z")
        OffsetDateTime finishedAt,

        @Schema(description = "Timestamp when the pipeline run record was created", example = "2026-03-20T14:44:58Z")
        OffsetDateTime createdAt
) {

    /**
     * Converts a {@link PipelineRun} entity to a summary DTO.
     *
     * @param run the pipeline run entity
     * @return the summary DTO
     */
    public static PipelineRunDto from(PipelineRun run) {
        return new PipelineRunDto(
                run.getId(),
                run.getRepoId(),
                run.getCommitSha(),
                run.getStatus(),
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt()
        );
    }
}
