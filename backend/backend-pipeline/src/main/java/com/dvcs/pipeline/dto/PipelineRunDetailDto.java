package com.dvcs.pipeline.dto;

import com.dvcs.pipeline.domain.PipelineRun;
import com.dvcs.pipeline.domain.PipelineStage;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
public record PipelineRunDetailDto(
        Long id,
        Long repoId,
        String commitSha,
        String status,
        List<PipelineStage> stages,
        OffsetDateTime startedAt,
        OffsetDateTime finishedAt,
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
