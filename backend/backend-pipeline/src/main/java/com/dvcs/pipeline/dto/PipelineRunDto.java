package com.dvcs.pipeline.dto;

import com.dvcs.pipeline.domain.PipelineRun;

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
public record PipelineRunDto(
        Long id,
        Long repoId,
        String commitSha,
        String status,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
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
