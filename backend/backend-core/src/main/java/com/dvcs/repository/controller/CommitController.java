package com.dvcs.repository.controller;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.dto.CommitMetaDto;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * REST controller for commit history and comparison operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/repos/{owner}/{repo}/commits/{branch} — paginated commit log</li>
 *   <li>GET /api/repos/{owner}/{repo}/commits/sha/{sha} — single commit detail</li>
 *   <li>GET /api/repos/{owner}/{repo}/compare/{base}...{head} — compare branches</li>
 * </ul>
 *
 * <p>Commit log pages are cached at {@code repo:{id}:commits:{branch}:{page}} with TTL 30s.
 */
@Tag(name = "Commits", description = "Commit history and comparison operations")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}")
public class CommitController {

    private static final Logger log = LoggerFactory.getLogger(CommitController.class);
    private static final long COMMITS_TTL_SECONDS = 30L;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CommitMetaRepository commitMetaRepository;
    private final BranchRepository branchRepository;
    private final RepoRepository repoRepository;
    private final com.dvcs.auth.repository.UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public CommitController(CommitMetaRepository commitMetaRepository,
                             BranchRepository branchRepository,
                             RepoRepository repoRepository,
                             com.dvcs.auth.repository.UserRepository userRepository,
                             StringRedisTemplate redisTemplate,
                             ObjectMapper objectMapper) {
        this.commitMetaRepository = commitMetaRepository;
        this.branchRepository = branchRepository;
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/commits/{branch}
    // -------------------------------------------------------------------------

    /**
     * Returns a paginated commit log for a branch, ordered newest first.
     * Results are cached in Redis.
     *
     * @param owner  the repository owner's username
     * @param repo   the repository name
     * @param branch the branch name
     * @param page   the page number (0-indexed, default 0)
     * @param size   the page size (default 20)
     * @return HTTP 200 with paginated commit list
     */
    @Operation(summary = "Get paginated commit log for a branch")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Commit log returned"),
        @ApiResponse(responseCode = "404", description = "Repository or branch not found")
    })
    @GetMapping("/commits/{branch}")
    public ResponseEntity<Map<String, Object>> getCommitLog(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String branch,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Long repoId = resolveRepoId(owner, repo);

        // Validate branch exists
        branchRepository.findByRepoIdAndName(repoId, branch)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + branch + "' not found in repository '" + owner + "/" + repo + "'."));

        // Try cache
        String cacheKey = "repo:" + repoId + ":commits:" + branch + ":" + page;
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                @SuppressWarnings("unchecked")
                Map<String, Object> cachedMap = objectMapper.readValue(cached, Map.class);
                return ResponseEntity.ok(cachedMap);
            }
        } catch (Exception e) {
            log.warn("Failed to read commit cache for repo {}: {}", repoId, e.getMessage());
        }

        Page<CommitMeta> commitPage = commitMetaRepository.findByRepoIdOrderByAuthoredAtDesc(
                repoId, PageRequest.of(page, size));

        List<CommitMetaDto> commits = commitPage.getContent()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("commits", commits);
        response.put("page", page);
        response.put("size", size);
        response.put("totalElements", commitPage.getTotalElements());
        response.put("totalPages", commitPage.getTotalPages());

        // Cache the result
        try {
            String json = objectMapper.writeValueAsString(response);
            redisTemplate.opsForValue().set(cacheKey, json, COMMITS_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache commits for repo {}: {}", repoId, e.getMessage());
        }

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/commits/sha/{sha}
    // -------------------------------------------------------------------------

    /**
     * Returns full metadata for a single commit identified by SHA.
     *
     * @param owner the repository owner's username
     * @param repo  the repository name
     * @param sha   the commit SHA
     * @return HTTP 200 with commit detail
     */
    @Operation(summary = "Get full metadata for a single commit by SHA")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Commit detail returned"),
        @ApiResponse(responseCode = "404", description = "Repository or commit not found")
    })
    @GetMapping("/commits/sha/{sha}")
    public ResponseEntity<Map<String, Object>> getCommit(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String sha) {

        Long repoId = resolveRepoId(owner, repo);

        CommitMeta commit = commitMetaRepository.findByRepoIdAndSha(repoId, sha)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Commit '" + sha + "' not found in repository '" + owner + "/" + repo + "'."));

        Map<String, Object> response = new HashMap<>();
        response.put("commit", toDto(commit));
        // Note: diff vs first parent would require the diff engine (task 9)
        // For now, return the commit metadata without diff
        response.put("diff", null);

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/compare/{base}...{head}
    // -------------------------------------------------------------------------

    /**
     * Compares two branches/refs and returns commits reachable from head but not base.
     *
     * @param owner    the repository owner's username
     * @param repo     the repository name
     * @param base     the base branch name
     * @param head     the head branch name
     * @return HTTP 200 with comparison result
     */
    @Operation(summary = "Compare two branches and return commits reachable from head but not base")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comparison result returned"),
        @ApiResponse(responseCode = "404", description = "Repository or branch not found")
    })
    @GetMapping("/compare/{base}...{head}")
    public ResponseEntity<Map<String, Object>> compare(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String base,
            @PathVariable String head) {

        Long repoId = resolveRepoId(owner, repo);

        Branch baseBranch = branchRepository.findByRepoIdAndName(repoId, base)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + base + "' not found in repository '" + owner + "/" + repo + "'."));

        Branch headBranch = branchRepository.findByRepoIdAndName(repoId, head)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + head + "' not found in repository '" + owner + "/" + repo + "'."));

        String baseSha = baseBranch.getHeadSha();
        String headSha = headBranch.getHeadSha();

        List<CommitMeta> commits = commitMetaRepository.findCommitsBetween(repoId, baseSha, headSha);

        List<CommitMetaDto> commitDtos = commits.stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        Map<String, Object> response = new HashMap<>();
        response.put("base", base);
        response.put("head", head);
        response.put("baseSha", baseSha);
        response.put("headSha", headSha);
        response.put("commits", commitDtos);
        response.put("commitCount", commitDtos.size());
        // Combined diff would require the diff engine (task 9)
        response.put("diff", null);

        return ResponseEntity.ok(response);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private Long resolveRepoId(String owner, String repoName) {
        com.dvcs.auth.domain.User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        Repository repository = repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));

        return repository.getId();
    }

    private CommitMetaDto toDto(CommitMeta commit) {
        return new CommitMetaDto(
                commit.getId(),
                commit.getSha(),
                commit.getAuthorId(),
                null, // author username lookup would require a join
                commit.getMessage(),
                commit.getAuthoredAt(),
                commit.getCommittedAt()
        );
    }
}
