package com.dvcs.repository.service;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.audit.Audited;
import com.dvcs.common.exception.AccessDeniedException;
import com.dvcs.common.exception.ConflictException;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.Collaborator;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.dto.CreateRepoRequest;
import com.dvcs.repository.dto.RepoDto;
import com.dvcs.repository.dto.RepoStatsDto;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.repository.GitObjectRepository;
import com.dvcs.repository.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Service for repository lifecycle management.
 *
 * <p>Handles create, read, delete, fork, and stats operations.
 * Enforces visibility rules per Req 3 and access control per Req 16.
 */
@Service
@Transactional
public class RepoService {

    private static final Logger log = LoggerFactory.getLogger(RepoService.class);

    private static final List<String> WRITE_ROLES = List.of("OWNER", "WRITE");
    private static final List<String> OWNER_ROLE = List.of("OWNER");

    /** Placeholder SHA used for the initial empty branch. */
    private static final String EMPTY_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final RepoRepository repoRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final BranchRepository branchRepository;
    private final CommitMetaRepository commitMetaRepository;
    private final GitObjectRepository gitObjectRepository;
    private final UserRepository userRepository;
    private final StringRedisTemplate redisTemplate;

    public RepoService(RepoRepository repoRepository,
                       CollaboratorRepository collaboratorRepository,
                       BranchRepository branchRepository,
                       CommitMetaRepository commitMetaRepository,
                       GitObjectRepository gitObjectRepository,
                       UserRepository userRepository,
                       StringRedisTemplate redisTemplate) {
        this.repoRepository = repoRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.branchRepository = branchRepository;
        this.commitMetaRepository = commitMetaRepository;
        this.gitObjectRepository = gitObjectRepository;
        this.userRepository = userRepository;
        this.redisTemplate = redisTemplate;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates a new repository for the given user.
     *
     * <p>Checks name uniqueness within the owner's namespace, creates the repository,
     * initializes the default branch, adds the creator as OWNER collaborator.
     *
     * @param userId  the ID of the user creating the repository
     * @param request the creation request
     * @return DTO of the created repository
     * @throws EntityNotFoundException if the user does not exist
     * @throws ConflictException       if a repository with the same name already exists for this user
     */
    public RepoDto createRepo(Long userId, CreateRepoRequest request) {
        User owner = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        // Check name uniqueness within owner namespace
        if (repoRepository.findByOwnerIdAndName(userId, request.name()).isPresent()) {
            throw new ConflictException(
                    "Repository '" + request.name() + "' already exists in your namespace.");
        }

        String defaultBranch = request.defaultBranch();

        Repository repo = Repository.builder()
                .ownerId(userId)
                .name(request.name())
                .description(request.description())
                .isPrivate(request.isPrivate())
                .defaultBranch(defaultBranch)
                .build();

        repo = repoRepository.save(repo);

        // Initialize default branch with empty SHA
        Branch branch = Branch.builder()
                .repoId(repo.getId())
                .name(defaultBranch)
                .headSha(EMPTY_SHA)
                .isProtected(false)
                .build();
        branchRepository.save(branch);

        // Add creator as OWNER collaborator
        Collaborator ownerCollab = Collaborator.builder()
                .repoId(repo.getId())
                .userId(userId)
                .role("OWNER")
                .build();
        collaboratorRepository.save(ownerCollab);

        return toDto(repo, owner.getUsername());
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Retrieves repository metadata, enforcing visibility rules.
     *
     * <p>Per Req 3.4, private repositories return 404 to non-collaborators.
     *
     * @param requesterId the ID of the requesting user (null for anonymous)
     * @param owner       the repository owner's username
     * @param name        the repository name
     * @return DTO of the repository
     * @throws EntityNotFoundException if the repository does not exist or is not accessible
     */
    @Transactional(readOnly = true)
    public RepoDto getRepo(Long requesterId, String owner, String name) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' does not exist or is not accessible."));

        Repository repo = repoRepository.findByOwnerIdAndName(ownerUser.getId(), name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' does not exist or is not accessible."));

        // Enforce visibility: private repos return 404 to non-collaborators
        if (repo.isPrivate()) {
            if (requesterId == null) {
                throw new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' does not exist or is not accessible.");
            }
            boolean isCollaborator = collaboratorRepository
                    .findByRepoIdAndUserId(repo.getId(), requesterId).isPresent();
            if (!isCollaborator) {
                throw new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' does not exist or is not accessible.");
            }
        }

        return toDto(repo, owner);
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a repository. Only the OWNER can delete.
     *
     * @param requesterId the ID of the requesting user
     * @param owner       the repository owner's username
     * @param name        the repository name
     * @throws EntityNotFoundException if the repository does not exist
     * @throws AccessDeniedException   if the requester is not the OWNER
     */
    @Audited(action = "delete_repo", resourceType = "repository")
    public void deleteRepo(Long requesterId, String owner, String name) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        Repository repo = repoRepository.findByOwnerIdAndName(ownerUser.getId(), name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        // Only OWNER can delete
        boolean isOwner = collaboratorRepository
                .existsByRepoIdAndUserIdAndRoleIn(repo.getId(), requesterId, OWNER_ROLE);
        if (!isOwner) {
            throw new AccessDeniedException("Only the repository owner can delete this repository.");
        }

        // Cascade delete is handled by FK constraints; just delete the repo
        repoRepository.delete(repo);

        // Invalidate cache
        evictRepoCache(repo.getId());
    }

    // -------------------------------------------------------------------------
    // Fork
    // -------------------------------------------------------------------------

    /**
     * Forks a repository into the caller's namespace.
     *
     * <p>Creates a new repository referencing the source, copies object references
     * (by setting fork_of), and initializes the default branch.
     *
     * @param userId the ID of the user forking
     * @param owner  the source repository owner's username
     * @param name   the source repository name
     * @return DTO of the forked repository
     * @throws EntityNotFoundException if the source repository does not exist or is not accessible
     * @throws ConflictException       if the user already has a repository with the same name
     */
    public RepoDto forkRepo(Long userId, String owner, String name) {
        User forkingUser = userRepository.findById(userId)
                .orElseThrow(() -> new EntityNotFoundException("User not found: " + userId));

        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        Repository source = repoRepository.findByOwnerIdAndName(ownerUser.getId(), name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        // Check visibility
        if (source.isPrivate()) {
            boolean isCollaborator = collaboratorRepository
                    .findByRepoIdAndUserId(source.getId(), userId).isPresent();
            if (!isCollaborator) {
                throw new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found.");
            }
        }

        // Check name uniqueness in forking user's namespace
        if (repoRepository.findByOwnerIdAndName(userId, name).isPresent()) {
            throw new ConflictException(
                    "Repository '" + name + "' already exists in your namespace.");
        }

        Repository fork = Repository.builder()
                .ownerId(userId)
                .name(name)
                .description(source.getDescription())
                .isPrivate(false) // forks are public by default
                .defaultBranch(source.getDefaultBranch())
                .forkOf(source.getId())
                .build();

        fork = repoRepository.save(fork);

        // Copy default branch head SHA from source
        Optional<Branch> sourceBranch = branchRepository
                .findByRepoIdAndName(source.getId(), source.getDefaultBranch());
        String headSha = sourceBranch.map(Branch::getHeadSha).orElse(EMPTY_SHA);

        Branch forkBranch = Branch.builder()
                .repoId(fork.getId())
                .name(fork.getDefaultBranch())
                .headSha(headSha)
                .isProtected(false)
                .build();
        branchRepository.save(forkBranch);

        // Add forking user as OWNER
        Collaborator ownerCollab = Collaborator.builder()
                .repoId(fork.getId())
                .userId(userId)
                .role("OWNER")
                .build();
        collaboratorRepository.save(ownerCollab);

        return toDto(fork, forkingUser.getUsername());
    }

    // -------------------------------------------------------------------------
    // Stats
    // -------------------------------------------------------------------------

    /**
     * Returns repository statistics: total object size, commit count, contributor count.
     *
     * @param owner the repository owner's username
     * @param name  the repository name
     * @return stats DTO
     * @throws EntityNotFoundException if the repository does not exist
     */
    @Transactional(readOnly = true)
    public RepoStatsDto getStats(String owner, String name) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        Repository repo = repoRepository.findByOwnerIdAndName(ownerUser.getId(), name)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + name + "' not found."));

        long totalSize = gitObjectRepository.sumSizeByRepoId(repo.getId());
        long commitCount = commitMetaRepository.countByRepoId(repo.getId());
        long contributorCount = collaboratorRepository.countByRepoId(repo.getId());

        return new RepoStatsDto(totalSize, commitCount, contributorCount);
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private RepoDto toDto(Repository repo, String ownerUsername) {
        return new RepoDto(
                repo.getId(),
                ownerUsername,
                repo.getName(),
                repo.getDescription(),
                repo.isPrivate(),
                repo.getDefaultBranch(),
                repo.getForkOf(),
                repo.getCreatedAt()
        );
    }

    private void evictRepoCache(Long repoId) {
        try {
            redisTemplate.delete("repo:" + repoId + ":meta");
            redisTemplate.delete("repo:" + repoId + ":branches");
        } catch (Exception e) {
            log.warn("Failed to evict cache for repo {}: {}", repoId, e.getMessage());
        }
    }
}
