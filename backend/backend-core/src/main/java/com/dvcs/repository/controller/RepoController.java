package com.dvcs.repository.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.common.security.RepoAccessGuard;
import com.dvcs.repository.dto.CreateRepoRequest;
import com.dvcs.repository.dto.RepoDto;
import com.dvcs.repository.dto.RepoStatsDto;
import com.dvcs.repository.service.RepoService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.concurrent.TimeUnit;

/**
 * REST controller for repository lifecycle operations.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/repos — create repository</li>
 *   <li>GET /api/repos/{owner}/{repo} — get repository metadata</li>
 *   <li>DELETE /api/repos/{owner}/{repo} — delete repository</li>
 *   <li>POST /api/repos/{owner}/{repo}/fork — fork repository</li>
 *   <li>GET /api/repos/{owner}/{repo}/stats — get repository stats</li>
 * </ul>
 *
 * <p>Repository metadata is cached in Redis at {@code repo:{id}:meta} with TTL 60s.
 */
@RestController
@RequestMapping("/api/repos")
public class RepoController {

    private static final Logger log = LoggerFactory.getLogger(RepoController.class);
    private static final long META_TTL_SECONDS = 60L;

    private final RepoService repoService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RepoController(RepoService repoService,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.repoService = repoService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // POST /api/repos
    // -------------------------------------------------------------------------

    /**
     * Creates a new repository for the authenticated user.
     *
     * @param request        the creation request
     * @param authentication the current authentication
     * @return HTTP 201 with the created repository DTO
     */
    @PostMapping
    public ResponseEntity<RepoDto> createRepo(
            @Valid @RequestBody CreateRepoRequest request,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        RepoDto dto = repoService.createRepo(user.getId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}
    // -------------------------------------------------------------------------

    /**
     * Returns repository metadata. Checks Redis cache first.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param authentication the current authentication (may be null for public repos)
     * @return HTTP 200 with the repository DTO
     */
    @GetMapping("/{owner}/{repo}")
    public ResponseEntity<RepoDto> getRepo(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        Long requesterId = user != null ? user.getId() : null;

        // Try cache first
        RepoDto dto = repoService.getRepo(requesterId, owner, repo);

        // Cache the result
        cacheRepoMeta(dto);

        return ResponseEntity.ok(dto);
    }

    // -------------------------------------------------------------------------
    // DELETE /api/repos/{owner}/{repo}
    // -------------------------------------------------------------------------

    /**
     * Deletes a repository. Only the OWNER can delete.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param authentication the current authentication
     * @return HTTP 204 No Content
     */
    @DeleteMapping("/{owner}/{repo}")
    public ResponseEntity<Void> deleteRepo(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        repoService.deleteRepo(user.getId(), owner, repo);
        return ResponseEntity.noContent().build();
    }

    // -------------------------------------------------------------------------
    // POST /api/repos/{owner}/{repo}/fork
    // -------------------------------------------------------------------------

    /**
     * Forks a repository into the authenticated user's namespace.
     *
     * @param owner          the source repository owner's username
     * @param repo           the source repository name
     * @param authentication the current authentication
     * @return HTTP 201 with the forked repository DTO
     */
    @PostMapping("/{owner}/{repo}/fork")
    public ResponseEntity<RepoDto> forkRepo(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        User user = RepoAccessGuard.extractUser(authentication);
        RepoDto dto = repoService.forkRepo(user.getId(), owner, repo);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/stats
    // -------------------------------------------------------------------------

    /**
     * Returns repository statistics.
     *
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @param authentication the current authentication
     * @return HTTP 200 with the stats DTO
     */
    @GetMapping("/{owner}/{repo}/stats")
    public ResponseEntity<RepoStatsDto> getStats(
            @PathVariable String owner,
            @PathVariable String repo,
            Authentication authentication) {

        RepoStatsDto stats = repoService.getStats(owner, repo);
        return ResponseEntity.ok(stats);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private void cacheRepoMeta(RepoDto dto) {
        try {
            String key = "repo:" + dto.id() + ":meta";
            String json = objectMapper.writeValueAsString(dto);
            redisTemplate.opsForValue().set(key, json, META_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache repo metadata for repo {}: {}", dto.id(), e.getMessage());
        }
    }
}
