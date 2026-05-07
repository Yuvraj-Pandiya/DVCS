package com.dvcs.pipeline.service;

import com.dvcs.git.event.PushEvent;
import com.dvcs.pipeline.domain.PipelineRun;
import com.dvcs.pipeline.domain.PipelineStage;
import com.dvcs.pipeline.repository.PipelineRunRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * CI/CD pipeline simulation engine.
 *
 * <p>Listens for {@link PushEvent} instances published by the Git transport layer
 * and asynchronously executes a two-stage pipeline (build → test) for each push.
 *
 * <p>Stage simulation:
 * <ul>
 *   <li><b>build</b> — sleeps 1–3 seconds, 10% random failure rate</li>
 *   <li><b>test</b> — sleeps 2–5 seconds, 15% random failure rate (only runs if build succeeds)</li>
 * </ul>
 *
 * <p>After completion, {@link #notifyOpenPRs(PipelineRun)} is called to update the
 * pipeline status display on any open pull requests targeting the pushed branch.
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
@Service
public class PipelineEngine {

    private static final Logger log = LoggerFactory.getLogger(PipelineEngine.class);

    private static final double BUILD_FAILURE_RATE = 0.10;
    private static final double TEST_FAILURE_RATE  = 0.15;

    private static final long BUILD_MIN_MS = 1_000L;
    private static final long BUILD_MAX_MS = 3_000L;
    private static final long TEST_MIN_MS  = 2_000L;
    private static final long TEST_MAX_MS  = 5_000L;

    private final PipelineRunRepository pipelineRunRepository;
    private final ObjectMapper objectMapper;
    private final Random random;

    public PipelineEngine(PipelineRunRepository pipelineRunRepository,
                          ObjectMapper objectMapper) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.objectMapper = objectMapper;
        this.random = new Random();
    }

    // =========================================================================
    // Event listener
    // =========================================================================

    /**
     * Handles a push event by creating and executing a pipeline run asynchronously.
     *
     * <p>This method is annotated with {@code @Async} so it runs in a separate
     * thread pool thread, allowing the push response to return immediately.
     *
     * @param event the push event containing repo ID and commit SHA
     */
    @Async
    @EventListener
    public void onPush(PushEvent event) {
        log.info("Pipeline triggered for repo={} branch={} commit={}",
                event.repoId(), event.branchName(), event.newHeadSha());

        // 1. Create PipelineRun with PENDING status
        PipelineRun run = PipelineRun.builder()
                .repoId(event.repoId())
                .commitSha(event.newHeadSha())
                .status("PENDING")
                .build();
        run = pipelineRunRepository.save(run);

        // 2. Transition to RUNNING and record start time
        run.setStatus("RUNNING");
        run.setStartedAt(OffsetDateTime.now());
        run = pipelineRunRepository.save(run);

        List<PipelineStage> stages = new ArrayList<>();

        // 3. Execute build stage
        PipelineStage buildStage = executeStage("build", BUILD_MIN_MS, BUILD_MAX_MS,
                BUILD_FAILURE_RATE);
        stages.add(buildStage);

        String finalStatus;

        if ("SUCCESS".equals(buildStage.getStatus())) {
            // 4. Execute test stage only if build succeeded
            PipelineStage testStage = executeStage("test", TEST_MIN_MS, TEST_MAX_MS,
                    TEST_FAILURE_RATE);
            stages.add(testStage);
            finalStatus = "SUCCESS".equals(testStage.getStatus()) ? "SUCCESS" : "FAILURE";
        } else {
            finalStatus = "FAILURE";
            log.info("Pipeline build stage failed for repo={} commit={} — skipping test stage",
                    event.repoId(), event.newHeadSha());
        }

        // 5. Persist final state
        run.setStatus(finalStatus);
        run.setFinishedAt(OffsetDateTime.now());
        run.setStagesJson(serializeStages(stages));
        run = pipelineRunRepository.save(run);

        log.info("Pipeline completed for repo={} commit={} status={}",
                event.repoId(), event.newHeadSha(), finalStatus);

        // 6. Notify open PRs about the pipeline result
        notifyOpenPRs(run);
    }

    // =========================================================================
    // PR notification
    // =========================================================================

    /**
     * Updates the pipeline status display on open pull requests targeting the
     * branch associated with this pipeline run.
     *
     * <p>Currently logs the pipeline completion. A full implementation would
     * query open PRs whose head branch matches the pushed branch and push a
     * real-time update via the notification service.
     *
     * @param run the completed pipeline run
     */
    public void notifyOpenPRs(PipelineRun run) {
        log.info("Pipeline run id={} status={} — notifying open PRs for repo={} commit={}",
                run.getId(), run.getStatus(), run.getRepoId(), run.getCommitSha());
        // Full implementation: query PullRequestRepository for open PRs with
        // head_sha == run.commitSha or head_branch matching the push branch,
        // then publish a notification via NotificationService / WebSocket.
        // Kept as a log-only stub to avoid a circular module dependency between
        // backend-pipeline and backend-pr.
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Simulates a single pipeline stage by sleeping for a random duration and
     * applying a random failure rate.
     *
     * @param name        the stage name (e.g., "build", "test")
     * @param minMs       minimum sleep duration in milliseconds
     * @param maxMs       maximum sleep duration in milliseconds
     * @param failureRate probability of failure (0.0–1.0)
     * @return the completed {@link PipelineStage} with timing and status
     */
    private PipelineStage executeStage(String name, long minMs, long maxMs, double failureRate) {
        OffsetDateTime stageStart = OffsetDateTime.now();
        log.debug("Stage '{}' starting", name);

        long sleepMs = minMs + (long) (random.nextDouble() * (maxMs - minMs));
        try {
            Thread.sleep(sleepMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Stage '{}' interrupted", name);
        }

        boolean failed = random.nextDouble() < failureRate;
        String status = failed ? "FAILURE" : "SUCCESS";
        OffsetDateTime stageEnd = OffsetDateTime.now();

        String logMessage = failed
                ? "Stage '" + name + "' failed after " + sleepMs + "ms"
                : "Stage '" + name + "' succeeded in " + sleepMs + "ms";

        log.debug("{}", logMessage);

        return new PipelineStage(name, status, stageStart, stageEnd, logMessage);
    }

    /**
     * Serializes the list of pipeline stages to a JSON string for storage in the
     * {@code stages_json} JSONB column.
     *
     * @param stages the list of completed stages
     * @return JSON string in the format {@code {"stages": [...]}}
     */
    private String serializeStages(List<PipelineStage> stages) {
        try {
            return objectMapper.writeValueAsString(Map.of("stages", stages));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize pipeline stages: {}", e.getMessage());
            return "{\"stages\":[]}";
        }
    }
}
