package com.dvcs.repository.controller;

import com.dvcs.common.exception.EntityNotFoundException;
import com.dvcs.common.validation.PathValidator;
import com.dvcs.repository.domain.Branch;
import com.dvcs.repository.domain.CommitMeta;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.dto.BlobDto;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;

/**
 * REST controller for retrieving file blobs from a repository.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/repos/{owner}/{repo}/blob/{ref}/** — returns Base64-encoded content + metadata</li>
 *   <li>GET /api/repos/{owner}/{repo}/raw/{ref}/**  — streams raw bytes with Content-Type detection</li>
 * </ul>
 *
 * <p>Blob content is cached in Redis at {@code blob:{repoId}:{sha}} with TTL 3600s
 * (handled by {@link com.dvcs.git.storage.ObjectStoreService}).
 *
 * <p>Requirement 7: File Tree and Blob Retrieval.
 * Requirement 19.2: Blob cache TTL 1 hour.
 */
@Tag(name = "Blobs", description = "Retrieve file blob content from a repository")
@RestController
@RequestMapping("/api/repos/{owner}/{repo}")
public class BlobController {

    private static final Logger log = LoggerFactory.getLogger(BlobController.class);

    private final RepoRepository repoRepository;
    private final com.dvcs.auth.repository.UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final CommitMetaRepository commitMetaRepository;
    private final GitObjectReaderService gitObjectReaderService;
    private final PathValidator pathValidator;

    public BlobController(RepoRepository repoRepository,
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
    // GET /api/repos/{owner}/{repo}/blob/{ref}/**
    // -------------------------------------------------------------------------

    /**
     * Returns the Base64-encoded content of a file at the given ref and path.
     *
     * @param owner   the repository owner's username
     * @param repo    the repository name
     * @param ref     the branch name or commit SHA
     * @param request the HTTP request (used to extract the wildcard path)
     * @return HTTP 200 with {@link BlobDto}
     */
    @Operation(summary = "Get Base64-encoded file content at a given ref and path")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Blob content returned"),
        @ApiResponse(responseCode = "400", description = "Path traversal detected"),
        @ApiResponse(responseCode = "401", description = "Authentication required for private repository"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Repository, ref, or file path not found")
    })
    @GetMapping({"/blob/{ref}", "/blob/{ref}/**"})
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<BlobDto> getBlob(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String ref,
            HttpServletRequest request) {

        String path = extractPath(request, owner, repo, ref, "blob");
        pathValidator.validate(path);

        Repository repository = resolveRepository(owner, repo);
        String repoIdStr = repository.getId().toString();

        String commitSha = resolveRef(repository.getId(), ref);
        String blobSha = resolveBlobSha(repoIdStr, commitSha, path);

        byte[] content;
        try {
            content = gitObjectReaderService.readBlobContent(repoIdStr, blobSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Blob '" + blobSha + "' not found.");
        }

        String lastCommitSha = findLastCommitSha(repoIdStr, repository.getId(), commitSha, path);
        String encoded = Base64.getEncoder().encodeToString(content);
        BlobDto dto = new BlobDto(encoded, content.length, "base64", lastCommitSha);

        return ResponseEntity.ok(dto);
    }

    // -------------------------------------------------------------------------
    // GET /api/repos/{owner}/{repo}/raw/{ref}/**
    // -------------------------------------------------------------------------

    /**
     * Streams the raw bytes of a file at the given ref and path.
     *
     * <p>The {@code Content-Type} header is detected from the file name extension
     * using {@link Files#probeContentType(Path)}, falling back to
     * {@code application/octet-stream} if detection fails.
     *
     * @param owner   the repository owner's username
     * @param repo    the repository name
     * @param ref     the branch name or commit SHA
     * @param request the HTTP request (used to extract the wildcard path)
     * @return HTTP 200 with raw file bytes and appropriate Content-Type
     */
    @Operation(summary = "Stream raw file bytes at a given ref and path")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Raw file bytes streamed with detected Content-Type"),
        @ApiResponse(responseCode = "400", description = "Path traversal detected"),
        @ApiResponse(responseCode = "401", description = "Authentication required for private repository"),
        @ApiResponse(responseCode = "403", description = "Access denied"),
        @ApiResponse(responseCode = "404", description = "Repository, ref, or file path not found")
    })
    @GetMapping({"/raw/{ref}", "/raw/{ref}/**"})
    @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
    public ResponseEntity<byte[]> getRaw(
            @PathVariable String owner,
            @PathVariable String repo,
            @PathVariable String ref,
            HttpServletRequest request) {

        String path = extractPath(request, owner, repo, ref, "raw");
        pathValidator.validate(path);

        Repository repository = resolveRepository(owner, repo);
        String repoIdStr = repository.getId().toString();

        String commitSha = resolveRef(repository.getId(), ref);
        String blobSha = resolveBlobSha(repoIdStr, commitSha, path);

        byte[] content;
        try {
            content = gitObjectReaderService.readBlobContent(repoIdStr, blobSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Blob '" + blobSha + "' not found.");
        }

        String contentType = detectContentType(path, content);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header(HttpHeaders.CONTENT_LENGTH, String.valueOf(content.length))
                .body(content);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Extracts the wildcard path portion from the request URI for blob/raw endpoints.
     */
    private static String extractPath(HttpServletRequest request,
                                      String owner, String repo, String ref,
                                      String endpoint) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty()) {
            uri = uri.substring(contextPath.length());
        }

        String prefix = "/api/repos/" + owner + "/" + repo + "/" + endpoint + "/" + ref;
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
     * Resolves a file path within a commit's tree to the blob SHA.
     *
     * @param repoId    the repository ID string
     * @param commitSha the commit SHA
     * @param path      the slash-separated file path
     * @return the blob SHA
     * @throws EntityNotFoundException if the path does not exist
     */
    private String resolveBlobSha(String repoId, String commitSha, String path) {
        if (path == null || path.isEmpty()) {
            throw new EntityNotFoundException("Path must not be empty for blob retrieval.");
        }

        String treeSha;
        try {
            treeSha = gitObjectReaderService.getCommitTreeSha(repoId, commitSha);
        } catch (IOException e) {
            throw new EntityNotFoundException("Commit '" + commitSha + "' not found.");
        }

        String[] segments = path.split("/");
        String currentTreeSha = treeSha;

        for (int i = 0; i < segments.length; i++) {
            String segment = segments[i];
            if (segment.isEmpty()) continue;

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
                            "Path '" + path + "' not found."));

            if (i == segments.length - 1) {
                // Last segment — must be a blob
                if (found.isTree()) {
                    throw new EntityNotFoundException(
                            "Path '" + path + "' is a directory, not a file.");
                }
                return found.sha();
            } else {
                // Intermediate segment — must be a tree
                if (!found.isTree()) {
                    throw new EntityNotFoundException(
                            "Path segment '" + segment + "' is a file, not a directory.");
                }
                currentTreeSha = found.sha();
            }
        }

        throw new EntityNotFoundException("Path '" + path + "' not found.");
    }

    /**
     * Finds the last commit SHA that touched the given file path.
     *
     * @param repoId     the repository ID string
     * @param repoIdLong the repository ID as Long
     * @param headSha    the head commit SHA
     * @param path       the file path
     * @return the last commit SHA that modified the file
     */
    private String findLastCommitSha(String repoId, Long repoIdLong,
                                     String headSha, String path) {
        int maxWalk = 200;
        String currentSha = headSha;
        int walked = 0;

        while (currentSha != null && walked < maxWalk) {
            List<String> parentShas;
            String currentTreeSha;
            try {
                currentTreeSha = gitObjectReaderService.getCommitTreeSha(repoId, currentSha);
                parentShas = gitObjectReaderService.getCommitParentShas(repoId, currentSha);
            } catch (Exception e) {
                break;
            }

            String parentSha = parentShas.isEmpty() ? null : parentShas.get(0);

            if (parentSha == null) {
                // Root commit — this commit introduced the file
                return currentSha;
            }

            // Compare blob SHA at path between this commit and parent
            String currentBlobSha = getBlobShaAtPath(repoId, currentTreeSha, path);
            String parentBlobSha = null;
            try {
                String parentTreeSha = gitObjectReaderService.getCommitTreeSha(repoId, parentSha);
                parentBlobSha = getBlobShaAtPath(repoId, parentTreeSha, path);
            } catch (Exception e) {
                log.debug("Could not read parent commit {}: {}", parentSha, e.getMessage());
            }

            if (currentBlobSha != null && !currentBlobSha.equals(parentBlobSha)) {
                return currentSha;
            }

            currentSha = parentSha;
            walked++;
        }

        return headSha;
    }

    /**
     * Gets the blob SHA at the given path within a tree, returning {@code null}
     * if the path does not exist.
     */
    private String getBlobShaAtPath(String repoId, String treeSha, String path) {
        try {
            String[] segments = path.split("/");
            String currentTreeSha = treeSha;

            for (int i = 0; i < segments.length; i++) {
                String segment = segments[i];
                if (segment.isEmpty()) continue;

                List<TreeEntryInfo> entries = gitObjectReaderService.listTreeEntries(repoId, currentTreeSha);
                TreeEntryInfo found = entries.stream()
                        .filter(e -> e.name().equals(segment))
                        .findFirst()
                        .orElse(null);

                if (found == null) return null;

                if (i == segments.length - 1) {
                    return found.sha();
                } else {
                    if (!found.isTree()) return null;
                    currentTreeSha = found.sha();
                }
            }
        } catch (Exception e) {
            log.debug("Could not get blob SHA at path {}: {}", path, e.getMessage());
        }
        return null;
    }

    /**
     * Detects the MIME type for the given file path and content.
     *
     * <p>Uses {@link Files#probeContentType(Path)} based on the file extension.
     * Falls back to magic-byte detection, then {@code application/octet-stream}.
     *
     * @param path    the file path (used for extension-based detection)
     * @param content the raw file content (used for magic-byte detection)
     * @return the detected MIME type string
     */
    static String detectContentType(String path, byte[] content) {
        // Try extension-based detection first
        if (path != null && !path.isEmpty()) {
            try {
                String fileName = path.contains("/") ? path.substring(path.lastIndexOf('/') + 1) : path;
                Path tempPath = Path.of(fileName);
                String probed = Files.probeContentType(tempPath);
                if (probed != null && !probed.isEmpty()) {
                    return probed;
                }
            } catch (Exception e) {
                log.debug("Content type probe failed for path {}: {}", path, e.getMessage());
            }
        }

        // Magic byte detection for common binary types
        if (content != null && content.length >= 4) {
            // PNG: \x89PNG
            if (content[0] == (byte) 0x89 && content[1] == 'P' && content[2] == 'N' && content[3] == 'G') {
                return "image/png";
            }
            // JPEG: \xFF\xD8\xFF
            if (content[0] == (byte) 0xFF && content[1] == (byte) 0xD8 && content[2] == (byte) 0xFF) {
                return "image/jpeg";
            }
            // GIF: GIF8
            if (content[0] == 'G' && content[1] == 'I' && content[2] == 'F' && content[3] == '8') {
                return "image/gif";
            }
            // PDF: %PDF
            if (content[0] == '%' && content[1] == 'P' && content[2] == 'D' && content[3] == 'F') {
                return "application/pdf";
            }
            // ZIP: PK\x03\x04
            if (content[0] == 'P' && content[1] == 'K' && content[2] == 0x03 && content[3] == 0x04) {
                return "application/zip";
            }
            // ELF: \x7fELF
            if (content[0] == 0x7f && content[1] == 'E' && content[2] == 'L' && content[3] == 'F') {
                return "application/octet-stream";
            }
        }

        // Check if content appears to be text (no null bytes in first 8000 bytes)
        if (content != null) {
            int checkLen = Math.min(content.length, 8000);
            boolean isText = true;
            for (int i = 0; i < checkLen; i++) {
                if (content[i] == 0) {
                    isText = false;
                    break;
                }
            }
            if (isText) {
                return "text/plain; charset=utf-8";
            }
        }

        return "application/octet-stream";
    }
}
