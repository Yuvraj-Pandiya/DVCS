package com.dvcs.diff.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.validation.PathValidator;
import com.dvcs.diff.algorithm.BinaryDetector;
import com.dvcs.diff.model.DiffHunk;
import com.dvcs.diff.service.BinaryDiffResult;
import com.dvcs.diff.service.DiffService;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.repository.service.GitObjectReaderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for computing diffs between commits in a repository.
 *
 * <h2>Endpoint</h2>
 * <pre>
 *   GET /api/repos/{owner}/{repo}/diff?base={ref}&head={ref}&path={filePath}
 * </pre>
 *
 * <p>The controller:
 * <ol>
 *   <li>Resolves {@code base} and {@code head} refs (branch names or commit SHAs)
 *       to commit SHAs.</li>
 *   <li>Reads the blob at {@code path} in the base commit to determine whether
 *       the file is binary (via {@link BinaryDetector}).</li>
 *   <li>Delegates to {@link DiffService#textDiff} for text files or
 *       {@link DiffService#binaryDiff} for binary files.</li>
 *   <li>Returns a JSON response: a list of {@link DiffHunk}s for text files, or
 *       a {@link BinaryDiffResult} for binary files.</li>
 * </ol>
 *
 * <p>Requirement 9.8: Diff Engine — DiffController.
 */
@Tag(name = "Diff", description = "Compute diffs between commits or refs")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}")
public class DiffController {

    private static final Logger log = LoggerFactory.getLogger(DiffController.class);

    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CommitMetaRepository commitMetaRepository;
    private final GitObjectReaderService gitObjectReaderService;
    private final DiffService diffService;
    private final PathValidator pathValidator;

    public DiffController(RepoRepository repoRepository,
                          UserRepository userRepository,
                          BranchRepository branchRepository,
                          CommitMetaRepository commitMetaRepository,
                          GitObjectReaderService gitObjectReaderService,
                          DiffService diffService,
                          PathValidator pathValidator) {
        this.repoRepository          = Objects.requireNonNull(repoRepository);
        this.userRepository          = Objects.requireNonNull(userRepository);
        this.branchRepository        = Objects.requireNonNull(branchRepository);
        this.commitMetaRepository    = Objects.requireNonNull(commitMetaRepository);
        this.gitObjectReaderService  = Objects.requireNonNull(gitObjectReaderService);
        this.diffService             = Objects.requireNonNull(diffService);
        this.pathValidator           = Objects.requireNonNull(pathValidator);
    }

    // =========================================================================
    // GET /api/repos/{owner}/{repo}/diff
    // =========================================================================

    /**
     * Returns the diff between two refs at an optional file path.
     *
     * <p>If {@code path} is provided, the diff is scoped to that single file.
     * If {@code path} is omitted, the endpoint returns HTTP 400 (a full-repo diff
     * is not supported in this implementation; use the compare endpoint instead).
     *
     * @param owner the repository owner's username
     * @param repo  the repository name
     * @param base  the base ref (branch name or commit SHA)
     * @param head  the head ref (branch name or commit SHA)
     * @param path  the file path to diff (required)
     * @return HTTP 200 with a list of {@link DiffHunk}s (text) or a
     *         {@link BinaryDiffResult} (binary)
     */
    @Operation(summary = "Compute diff between two refs for a specific file path")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Diff computed and returned"),
        @ApiResponse(responseCode = "400", description = "Missing or invalid path parameter"),
        @ApiResponse(responseCode = "401", description = "Authentication required for private repository"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Repository, ref, or file path not found")
    })
    @GetMapping("/diff")
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<Object> getDiff(
            @PathVariable String owner,
            @PathVariable String repo,
            @RequestParam String base,
            @RequestParam String head,
            @RequestParam(required = false) String path) {

        if (path == null || path.isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ErrorBody("MISSING_PARAMETER",
                            "Query parameter 'path' is required for file-level diff."));
        }

        pathValidator.validate(path);

        Repository repository = resolveRepository(owner, repo);
        String repoIdStr = repository.getId().toString();

        String baseSha = resolveRef(repository.getId(), base);
        String headSha = resolveRef(repository.getId(), head);

        // Determine whether the file is binary by reading the base blob
        boolean binary = isFileBinary(repoIdStr, baseSha, path);

        try {
            if (binary) {
                BinaryDiffResult result = diffService.binaryDiff(repoIdStr, baseSha, headSha, path);
                return ResponseEntity.ok(result);
            } else {
                List<DiffHunk> hunks = diffService.textDiff(repoIdStr, baseSha, headSha, path);
                return ResponseEntity.ok(hunks);
            }
        } catch (IOException e) {
            log.error("Failed to compute diff for {}/{} base={} head={} path={}",
                    owner, repo, base, head, path, e);
            throw new EntityNotFoundException(
                    "Could not compute diff: " + e.getMessage());
        }
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Resolves the repository entity by owner username and repository name.
     */
    private Repository resolveRepository(String owner, String repoName) {
        User ownerUser = userRepository.findByUsername(owner)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));
        return repoRepository.findByOwnerIdAndName(ownerUser.getId(), repoName)
                .orElseThrow(() -> new EntityNotFoundException(
                        "Repository '" + owner + "/" + repoName + "' not found."));
    }

    /**
     * Resolves a ref (branch name or commit SHA) to a commit SHA.
     */
    private String resolveRef(Long repoId, String ref) {
        return branchRepository.findByRepoIdAndName(repoId, ref)
                .map(Branch::getHeadSha)
                .orElseGet(() -> {
                    CommitMeta meta = commitMetaRepository.findByRepoIdAndSha(repoId, ref)
                            .orElseThrow(() -> new EntityNotFoundException(
                                    "Ref '" + ref + "' not found."));
                    return meta.getSha();
                });
    }

    /**
     * Determines whether the file at {@code path} in the given commit is binary.
     *
     * <p>Reads the first bytes of the blob and delegates to {@link BinaryDetector}.
     * Returns {@code false} (treat as text) if the blob cannot be read.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @param path      the file path
     * @return {@code true} if the file appears to be binary
     */
    private boolean isFileBinary(String repoId, String commitSha, String path) {
        try {
            // Resolve blob SHA via commit tree
            String treeSha = gitObjectReaderService.getCommitTreeSha(repoId, commitSha);
            String blobSha = resolveBlobShaInTree(repoId, treeSha, path);
            byte[] content = gitObjectReaderService.readBlobContent(repoId, blobSha);
            return BinaryDetector.isBinary(content);
        } catch (Exception e) {
            log.debug("Could not determine binary status for path '{}' in commit '{}': {}",
                    path, commitSha, e.getMessage());
            return false;
        }
    }

    /**
     * Resolves a file path within a tree to the blob SHA.
     *
     * @param repoId  the repository ID string
     * @param treeSha the root tree SHA
     * @param path    the slash-separated file path
     * @return the blob SHA
     * @throws EntityNotFoundException if the path does not exist
     * @throws IOException             if an object cannot be read
     */
    private String resolveBlobShaInTree(String repoId, String treeSha, String path)
            throws IOException {
        String[] segments = path.split("/");
        String currentTreeSha = treeSha;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;

            var entries = gitObjectReaderService.listTreeEntries(repoId, currentTreeSha);
            var found = entries.stream()
                    .filter(e -> e.name().equals(segment))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Path '" + path + "' not found."));

            if (i == segments.length - 1) {
                return found.sha();
            } else {
                currentTreeSha = found.sha();
            }
        }
        throw new EntityNotFoundException("Path '" + path + "' not found.");
    }

    // =========================================================================
    // Internal response types
    // =========================================================================

    /**
     * Simple error body for 400 responses.
     *
     * @param error   the error code
     * @param message the human-readable message
     */
    private record ErrorBody(String error, String message) {}
}
