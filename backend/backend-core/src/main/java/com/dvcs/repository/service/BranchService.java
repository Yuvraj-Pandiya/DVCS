package com.dvcs.repository.service;

import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.ConflictException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.dto.BranchDto;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Service for branch management operations.
 *
 * <p>Handles create, list, delete, and protection toggle for branches.
 * Branch lists are cached in Redis at {@code repo:{id}:branches} with TTL 60s.
 */
@Service
@Transactional
public class BranchService {

    private static final Logger log = LoggerFactory.getLogger(BranchService.class);
    private static final long BRANCHES_TTL_SECONDS = 60L;
    private static final List<String> WRITE_ROLES = List.of("OWNER", "WRITE");

    private final BranchRepository branchRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final CommitMetaRepository commitMetaRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public BranchService(BranchRepository branchRepository,
                         CollaboratorRepository collaboratorRepository,
                         CommitMetaRepository commitMetaRepository,
                         StringRedisTemplate redisTemplate,
                         ObjectMapper objectMapper) {
        this.branchRepository = branchRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.commitMetaRepository = commitMetaRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new branch in the repository.
     *
     * @param repoId    the repository ID
     * @param name      the branch name
     * @param sourceSha the SHA to point the branch at
     * @return DTO of the created branch
     * @throws ConflictException if a branch with the same name already exists
     */
    public BranchDto createBranch(Long repoId, String name, String sourceSha) {
        if (branchRepository.findByRepoIdAndName(repoId, name).isPresent()) {
            throw new ConflictException("Branch '" + name + "' already exists in this repository.");
        }

        Branch branch = Branch.builder()
                .repoId(repoId)
                .name(name)
                .headSha(sourceSha)
                .isProtected(false)
                .build();

        branch = branchRepository.save(branch);

        // Invalidate branch list cache
        evictBranchCache(repoId);

        return toDto(branch);
    }

    // -------------------------------------------------------------------------
    // List
    // -------------------------------------------------------------------------

    /**
     * Lists all branches for a repository. Results are cached in Redis.
     *
     * @param repoId the repository ID
     * @return list of branch DTOs
     */
    @Transactional(readOnly = true)
    public List<BranchDto> listBranches(Long repoId) {
        // Try cache first
        String cacheKey = "repo:" + repoId + ":branches";
        try {
            String cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached != null) {
                return objectMapper.readValue(cached,
                        objectMapper.getTypeFactory().constructCollectionType(List.class, BranchDto.class));
            }
        } catch (Exception e) {
            log.warn("Failed to read branch cache for repo {}: {}", repoId, e.getMessage());
        }

        List<BranchDto> branches = branchRepository.findByRepoId(repoId)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());

        // Cache the result
        try {
            String json = objectMapper.writeValueAsString(branches);
            redisTemplate.opsForValue().set(cacheKey, json, BRANCHES_TTL_SECONDS, TimeUnit.SECONDS);
        } catch (JsonProcessingException e) {
            log.warn("Failed to cache branches for repo {}: {}", repoId, e.getMessage());
        }

        return branches;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a branch. Protected branches cannot be deleted.
     *
     * @param repoId      the repository ID
     * @param name        the branch name
     * @param requesterId the ID of the requesting user
     * @throws EntityNotFoundException if the branch does not exist
     * @throws AccessDeniedException   if the branch is protected (Req 5.5)
     */
    public void deleteBranch(Long repoId, String name, Long requesterId) {
        Branch branch = branchRepository.findByRepoIdAndName(repoId, name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + name + "' not found in repository."));

        if (branch.isProtected()) {
            throw new AccessDeniedException(
                    "Branch '" + name + "' is protected and cannot be deleted.");
        }

        branchRepository.delete(branch);

        // Invalidate branch list cache
        evictBranchCache(repoId);
    }

    // -------------------------------------------------------------------------
    // Toggle protection
    // -------------------------------------------------------------------------

    /**
     * Toggles the protection flag on a branch.
     *
     * @param repoId      the repository ID
     * @param name        the branch name
     * @param protect     whether to protect or unprotect
     * @param requesterId the ID of the requesting user
     * @return updated branch DTO
     * @throws EntityNotFoundException if the branch does not exist
     * @throws AccessDeniedException   if the requester lacks OWNER or WRITE role
     */
    public BranchDto toggleProtection(Long repoId, String name, boolean protect, Long requesterId) {
        // Check OWNER or WRITE role
        boolean hasWriteAccess = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(repoId, requesterId, WRITE_ROLES);
        if (!hasWriteAccess) {
            throw new AccessDeniedException(
                    "You need OWNER or WRITE role to modify branch protection.");
        }

        Branch branch = branchRepository.findByRepoIdAndName(repoId, name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Branch '" + name + "' not found in repository."));

        branch.setProtected(protect);
        branch = branchRepository.save(branch);

        // Invalidate branch list cache
        evictBranchCache(repoId);

        return toDto(branch);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private BranchDto toDto(Branch branch) {
        // Fetch the last commit date for this branch
        OffsetDateTime lastCommitDate = commitMetaRepository
                .findByRepoIdAndSha(branch.getRepoId(), branch.getHeadSha())
                .map(CommitMeta::getCommittedAt)
                .orElse(null);

        return new BranchDto(
                branch.getId(),
                branch.getRepoId(),
                branch.getName(),
                branch.getHeadSha(),
                branch.isProtected(),
                branch.getCreatedAt(),
                lastCommitDate
        );
    }

    private void evictBranchCache(Long repoId) {
        try {
            redisTemplate.delete("repo:" + repoId + ":branches");
        } catch (Exception e) {
            log.warn("Failed to evict branch cache for repo {}: {}", repoId, e.getMessage());
        }
    }
}
