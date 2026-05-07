package com.dvcs.repository.controller;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.validation.PathValidator;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.dto.TreeEntryDto;
import com.dvcs.repository.repository.BranchRepository;
import com.dvcs.repository.repository.CommitMetaRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.repository.service.GitObjectReaderService;
import com.dvcs.repository.service.GitObjectReaderService.TreeEntryInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REST controller for browsing the file tree of a repository at a given ref and path.
 *
 * <p>Endpoint:
 * <ul>
 *   <li>GET /api/repos/{owner}/{repo}/tree/{ref}/**  — list directory entries</li>
 * </ul>
 *
 * <p>Resolution flow:
 * <ol>
 *   <li>Resolve {@code ref} to a branch head SHA via {@code branches} table.</li>
 *   <li>Read the commit object to get {@code treeSha}.</li>
 *   <li>Walk tree entries along each path segment.</li>
 *   <li>For each entry in the final directory, find the last commit that touched it
 *       by scanning commit history.</li>
 * </ol>
 *
 * <p>Requirement 7: File Tree and Blob Retrieval.
 */
@Tag(name = "File Tree", description = "Browse repository file tree at a given ref and path")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}/tree")
public class TreeController {

    private static final Logger log = LoggerFactory.getLogger(TreeController.class);

    private final RepoRepository repoRepository;
    private final com.dvcs.auth.repository.UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CommitMetaRepository commitMetaRepository;
    private final GitObjectReaderService gitObjectReaderService;
    private final PathValidator pathValidator;

    public TreeController(RepoRepository repoRepository,
                          com.dvcs.auth.repository.UserRepository userRepository,
                          BranchRepository branchRepository,
                          CommitMetaRepository commitMetaRepository,
                          GitObjectReaderService gitObjectReaderService,
                          PathValidator pathValidator) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.branchRepository = branchRepository;
        this.commitMetaRepository = commitMetaRepository;
        this.gitObjectReaderService = gitObjectReaderService;
        this.pathValidator = pathValidator;
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/tree/{ref}/**
    // -------------------------------------------------------------------------

    /**
     * Returns a directory listing for the given ref and path.
     *
     * <p>The {@code path} is captured as a wildcard segment. An empty path means
     * the repository root.
     *
     * @param owner   the repository owner's username
     * @param repo    the repository name
     * @param ref     the branch name or commit SHA
     * @param request the HTTP request (used to extract the wildcard path)
     * @return HTTP 200 with a JSON array of {@link TreeEntryDto}
     */
    @Operation(summary = "List directory entries at a given ref and path")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Directory listing returned"),
        @ApiResponse(responseCode = "400", description = "Path traversal detected"),
        @ApiResponse(responseCode = "401", description = "Authentication required for private repository"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Repository, ref, or path not found")
    })
    @GetMapping({"/{ref}", "/{ref}/**"})
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<List<TreeEntryDto>> getTree(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String ref,
            HttpServletRequest request) {

        // Extract the wildcard path from the request URI
        String path = extractPath(request, owner, repo, ref);

        // Validate path for traversal sequences
        pathValidator.validate(path);

        // Resolve repository
        Repository repository = resolveRepository(owner, repo);
        String repoIdStr = repository.getId().toString();

        // Resolve ref to commit SHA
        String commitSha = resolveRef(repository.getId(), ref);

        // Read commit to get root tree SHA
        String treeSha;
        try {
            treeSha = gitObjectReaderService.getCommitTreeSha(repoIdStr, commitSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Commit '" + commitSha + "' not found.");
        }

        // Walk path segments to find the target tree
        if (path != null && !path.isEmpty()) {
            treeSha = walkPath(repoIdStr, treeSha, path);
        }

        // Read the target tree entries
        List<TreeEntryInfo> entries;
        try {
            entries = gitObjectReaderService.listTreeEntries(repoIdStr, treeSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Tree '" + treeSha + "' not found.");
        }

        // Build response entries with lastCommitSha
        List<TreeEntryDto> result = buildEntries(
                repoIdStr, repository.getId(), entries, path, commitSha);

        return ResponseEntity.ok(result);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the wildcard path portion from the request URI.
     *
     * <p>The URI pattern is {@code /api/repos/{owner}/{repo}/tree/{ref}/**}.
     * This method strips the prefix up to and including the ref segment.
     *
     * @param request the HTTP request
     * @param owner   the owner path variable
     * @param repo    the repo path variable
     * @param ref     the ref path variable
     * @return the remaining path after the ref, or empty string for root
     */
    private static String extractPath(HttpServletRequest request,
                                      String owner, String repo, String ref) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            uri = uri.substring(contextPath.length());
        }

        String prefix = "/api/repos/" + owner + "/" + repo + "/tree/" + ref;
        if (uri.startsWith(prefix)) {
            String remaining = uri.substring(prefix.length());
            if (remaining.startsWith("/")) {
                remaining = remaining.substring(1);
            }
            return remaining;
        }
        return "";
    }

    /**
     * Resolves the repository entity by owner username and repository name.
     */
    private Repository resolveRepository(String owner, String repoName) {
        com.dvcs.auth.domain.User ownerUser = userRepository.findByUsername(owner)
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
     * Walks the tree along the given path segments, returning the SHA of the
     * tree at the end of the path.
     *
     * @param repoId  the repository ID string
     * @param treeSha the root tree SHA
     * @param path    the slash-separated path (e.g. {@code "src/main/java"})
     * @return the SHA of the tree at the given path
     * @throws EntityNotFoundException if any path segment is not found
     */
    private String walkPath(String repoId, String treeSha, String path) {
        String[] segments = path.split("/");
        String currentTreeSha = treeSha;

        for (String segment : segments) {
            if (segment.isEmpty()) {
                continue;
            }
            List<TreeEntryInfo> entries;
            try {
                entries = gitObjectReaderService.listTreeEntries(repoId, currentTreeSha);
            } catch (IOException e) {
                throw new EntityNotFoundException("Tree not found at path segment '" + segment + "'.");
            }

            TreeEntryInfo found = entries.stream()
                    .filter(e -> e.name().equals(segment))
                    .findFirst()
                    .orElseThrow(() -> new EntityNotFoundException(
                            "Path segment '" + segment + "' not found."));

            if (found.isTree()) {
                currentTreeSha = found.sha();
            } else {
                throw new EntityNotFoundException(
                        "Path '" + path + "' is a file, not a directory.");
            }
        }
        return currentTreeSha;
    }

    /**
     * Builds the list of {@link TreeEntryDto} for the given entries,
     * including the last commit SHA and message for each entry.
     *
     * @param repoId     the repository ID string
     * @param repoIdLong the repository ID as Long
     * @param entries    the tree entries to list
     * @param dirPath    the path of this directory (used for history walking)
     * @param headSha    the head commit SHA to start history walk from
     * @return list of tree entry DTOs
     */
    private List<TreeEntryDto> buildEntries(String repoId, Long repoIdLong,
                                             List<TreeEntryInfo> entries, String dirPath,
                                             String headSha) {
        Map<String, CommitInfo> lastCommitMap = findLastCommits(
                repoId, repoIdLong, entries, dirPath, headSha);

        List<TreeEntryDto> result = new ArrayList<>();
        for (TreeEntryInfo entry : entries) {
            long size = 0L;
            if (!entry.isTree()) {
                try {
                    size = gitObjectReaderService.getBlobContentSize(repoId, entry.sha());
                } catch (IOException e) {
                    log.debug("Could not get blob size for {}: {}", entry.sha(), e.getMessage());
                }
            }

            CommitInfo commitInfo = lastCommitMap.get(entry.name());
            String lastCommitSha = commitInfo != null ? commitInfo.sha() : headSha;
            String lastCommitMessage = commitInfo != null ? commitInfo.message() : "";

            result.add(new TreeEntryDto(entry.name(), entry.type(), size, lastCommitSha, lastCommitMessage));
        }
        return result;
    }

    /**
     * Finds the last commit that touched each entry in the given tree by walking
     * the commit history from {@code headSha}.
     *
     * @param repoId     the repository ID string
     * @param repoIdLong the repository ID as Long
     * @param entries    the current tree entries
     * @param dirPath    the path of this directory
     * @param headSha    the head commit SHA
     * @return map of entry name → last commit info
     */
    private Map<String, CommitInfo> findLastCommits(String repoId, Long repoIdLong,
                                                     List<TreeEntryInfo> entries, String dirPath,
                                                     String headSha) {
        Map<String, CommitInfo> result = new HashMap<>();

        int maxWalk = 200;
        String currentSha = headSha;
        int walked = 0;

        while (currentSha != null && walked < maxWalk && result.size() < entries.size()) {
            String commitTreeSha;
            List<String> parentShas;
            String commitMessage;
            try {
                commitTreeSha = gitObjectReaderService.getCommitTreeSha(repoId, currentSha);
                parentShas = gitObjectReaderService.getCommitParentShas(repoId, currentSha);
                commitMessage = gitObjectReaderService.getCommitMessage(repoId, currentSha);
            } catch (Exception e) {
                log.debug("Could not read commit {} while walking history: {}", currentSha, e.getMessage());
                break;
            }

            // Use DB message if available (may be more reliable)
            String dbMessage = commitMetaRepository.findByRepoIdAndSha(repoIdLong, currentSha)
                    .map(CommitMeta::getMessage)
                    .orElse(commitMessage);

            // Get entries at this commit's directory
            Map<String, String> currentEntryShas = getEntryShasAtPath(repoId, commitTreeSha, dirPath);

            // Get parent entries for comparison
            String parentSha = parentShas.isEmpty() ? null : parentShas.get(0);
            Map<String, String> parentEntryShas = new HashMap<>();
            if (parentSha != null) {
                try {
                    String parentTreeSha = gitObjectReaderService.getCommitTreeSha(repoId, parentSha);
                    parentEntryShas = getEntryShasAtPath(repoId, parentTreeSha, dirPath);
                } catch (Exception e) {
                    log.debug("Could not read parent commit {}: {}", parentSha, e.getMessage());
                }
            }

            // Check each entry
            for (TreeEntryInfo entry : entries) {
                if (result.containsKey(entry.name())) {
                    continue;
                }
                String currentEntrySha = currentEntryShas.get(entry.name());
                if (currentEntrySha == null) {
                    continue; // Entry doesn't exist at this commit
                }
                String parentEntrySha = parentEntryShas.get(entry.name());
                // Entry is new or changed in this commit
                if (parentEntrySha == null || !parentEntrySha.equals(currentEntrySha)) {
                    result.put(entry.name(), new CommitInfo(currentSha, dbMessage));
                }
            }

            currentSha = parentSha;
            walked++;
        }

        // Fill in any remaining entries with the head commit
        if (result.size() < entries.size()) {
            String headMessage = commitMetaRepository.findByRepoIdAndSha(repoIdLong, headSha)
                    .map(CommitMeta::getMessage)
                    .orElse("");
            for (TreeEntryInfo entry : entries) {
                if (!result.containsKey(entry.name())) {
                    result.put(entry.name(), new CommitInfo(headSha, headMessage));
                }
            }
        }

        return result;
    }

    /**
     * Gets a map of entry name → SHA for the entries at the given path within a tree.
     * Returns an empty map if the path doesn't exist.
     *
     * @param repoId  the repository ID string
     * @param treeSha the root tree SHA
     * @param dirPath the directory path (may be null or empty for root)
     * @return map of entry name → SHA
     */
    private Map<String, String> getEntryShasAtPath(String repoId, String treeSha, String dirPath) {
        Map<String, String> result = new HashMap<>();
        try {
            String targetTreeSha = treeSha;
            if (dirPath != null && !dirPath.isEmpty()) {
                targetTreeSha = walkPathSilent(repoId, treeSha, dirPath);
                if (targetTreeSha == null) {
                    return result;
                }
            }
            List<TreeEntryInfo> entries = gitObjectReaderService.listTreeEntries(repoId, targetTreeSha);
            for (TreeEntryInfo entry : entries) {
                result.put(entry.name(), entry.sha());
            }
        } catch (Exception e) {
            log.debug("Could not get entries at path {}: {}", dirPath, e.getMessage());
        }
        return result;
    }

    /**
     * Walks a path silently, returning {@code null} if any segment is not found.
     */
    private String walkPathSilent(String repoId, String treeSha, String path) {
        try {
            return walkPath(repoId, treeSha, path);
        } catch (EntityNotFoundException e) {
            return null;
        }
    }

    /**
     * Internal record holding commit SHA and message for last-commit tracking.
     */
    private record CommitInfo(String sha, String message) {}
}
