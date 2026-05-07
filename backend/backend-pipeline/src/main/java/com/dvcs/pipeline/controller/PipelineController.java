package com.dvcs.pipeline.controller;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.pipeline.domain.PipelineRun;
import com.dvcs.pipeline.dto.PipelineRunDetailDto;
import com.dvcs.pipeline.dto.PipelineRunDto;
import com.dvcs.pipeline.repository.PipelineRunRepository;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for CI/CD pipeline run operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/repos/{owner}/{repo}/pipelines — paginated list of pipeline runs</li>
 *   <li>GET /api/pipelines/{id} — full pipeline run detail with parsed stage list</li>
 * </ul>
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
@RestController
public class PipelineController {

    private final PipelineRunRepository pipelineRunRepository;
    private final RepoRepository repoRepository;
    private final com.dvcs.auth.repository.UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public PipelineController(PipelineRunRepository pipelineRunRepository,
                               RepoRepository repoRepository,
                               com.dvcs.auth.repository.UserRepository userRepository,
                               ObjectMapper objectMapper) {
        this.pipelineRunRepository = pipelineRunRepository;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/pipelines
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated list of pipeline runs for a repository, ordered by
     * creation date descending (newest first).
     *
     * @param owner    the repository owner's username
     * @param repo     the repository name
     * @param pageable pagination parameters (default page size: 20)
     * @return HTTP 200 with a page of {@link PipelineRunDto}
     */
    @GetMapping("/api/repos/{owner}/{repo}/pipelines")
    public ResponseEntity<Page<PipelineRunDto>> listPipelineRuns(
            @PathVariable String owner,
            @PathVariable String repo,
            @PageableDefault(size = 20) Pageable pageable) {

        Long repoId = resolveRepoId(owner, repo);
        Page<PipelineRunDto> page = pipelineRunRepository
                .findByRepoIdOrderByCreatedAtDesc(repoId, pageable)
                .map(PipelineRunDto::from);
        return ResponseEntity.ok(page);
    }

    // -------------------------------------------------------------------------
    // GET /api/pipelines/{id}
    // -------------------------------------------------------------------------

    /**
     * Returns the full detail of a single pipeline run, including the parsed
     * stage list from the {@code stages_json} JSONB column.
     *
     * @param id the pipeline run ID
     * @return HTTP 200 with a {@link PipelineRunDetailDto}
     * @throws EntityNotFoundException if no pipeline run with the given ID exists
     */
    @GetMapping("/api/pipelines/{id}")
    public ResponseEntity<PipelineRunDetailDto> getPipelineRun(@PathVariable Long id) {
        PipelineRun run = pipelineRunRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Pipeline run with id=" + id + " not found."));
        return ResponseEntity.ok(PipelineRunDetailDto.from(run, objectMapper));
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the internal repository ID from owner username and repository name.
     *
     * @param owner    the repository owner's username
     * @param repoName the repository name
     * @return the internal repository ID
     * @throws EntityNotFoundException if the owner or repository does not exist
     */
    private Long resolveRepoId(String owner, String repoName) {
        com.dvcs.auth.domain.User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        Repository repository = repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        return repository.getId();
    }
}
