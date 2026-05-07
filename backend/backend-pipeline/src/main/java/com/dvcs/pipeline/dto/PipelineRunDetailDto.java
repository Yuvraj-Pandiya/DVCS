package com.dvcs.pipeline.dto;

import com.dvcs.pipeline.domain.PipelineRun;
import com.dvcs.pipeline.domain.PipelineStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.media.Schema;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * DTO for a full pipeline run detail response, including parsed stage list.
 *
 * <p>The {@code stagesJson} JSONB column is parsed into a typed {@link List} of
 * {@link PipelineStage} objects for the API response.
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 *
 * @param id         the pipeline run ID
 * @param repoId     the repository ID
 * @param commitSha  the commit SHA that triggered the run
 * @param status     the run status (PENDING, RUNNING, SUCCESS, FAILURE)
 * @param stages     the parsed list of stage results
 * @param startedAt  when the run started (null if still PENDING)
 * @param finishedAt when the run finished (null if not yet complete)
 * @param createdAt  when the run record was created
 */
@Schema(description = "Full pipeline run detail including parsed stage results")
public record PipelineRunDetailDto(
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

        @Schema(description = "List of stage results parsed from the stages_json column")
        List<PipelineStage> stages,

        @Schema(description = "Timestamp when the pipeline run started; null if still PENDING",
                example = "2026-03-20T14:45:00Z")
        OffsetDateTime startedAt,

        @Schema(description = "Timestamp when the pipeline run finished; null if not yet complete",
                example = "2026-03-20T14:47:30Z")
        OffsetDateTime finishedAt,

        @Schema(description = "Timestamp when the pipeline run record was created", example = "2026-03-20T14:44:58Z")
        OffsetDateTime createdAt
) {

    private static final Logger log = LoggerFactory.getLogger(PipelineRunDetailDto.class);

    /**
     * Converts a {@link PipelineRun} entity to a detail DTO, parsing the
     * {@code stagesJson} JSONB column into a typed stage list.
     *
     * @param run          the pipeline run entity
     * @param objectMapper the Jackson mapper used to parse {@code stagesJson}
     * @return the detail DTO
     */
    public static PipelineRunDetailDto from(PipelineRun run, ObjectMapper objectMapper) {
        List<PipelineStage> stages = parseStages(run.getStagesJson(), objectMapper);
        return new PipelineRunDetailDto(
                run.getId(),
                run.getRepoId(),
                run.getCommitSha(),
                run.getStatus(),
                stages,
                run.getStartedAt(),
                run.getFinishedAt(),
                run.getCreatedAt()
        );
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the {@code stages_json} JSONB string into a list of {@link PipelineStage}.
     *
     * <p>Expected format: {@code {"stages": [{...}, ...]}}
     *
     * @param stagesJson   the raw JSON string from the database column
     * @param objectMapper the Jackson mapper
     * @return parsed stage list, or an empty list if the JSON is null or unparseable
     */
    private static List<PipelineStage> parseStages(String stagesJson, ObjectMapper objectMapper) {
        if (stagesJson == null || stagesJson.isBlank()) {
            return Collections.emptyList();
        }
        try {
            Map<String, List<PipelineStage>> wrapper = objectMapper.readValue(
                    stagesJson,
                    new TypeReference<Map<String, List<PipelineStage>>>() {}
            );
            List<PipelineStage> stages = wrapper.get("stages");
            return stages != null ? stages : Collections.emptyList();
        } catch (JsonProcessingException e) {
            log.warn("Failed to parse stages_json: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
