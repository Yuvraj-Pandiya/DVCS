package com.dvcs.search.service;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.repository.domain.GitObject;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.GitObjectRepository;
import com.dvcs.repository.repository.RepoRepository;
import com.dvcs.repository.service.GitObjectReaderService;
import com.dvcs.search.dto.CodeSearchResult;
import com.dvcs.search.dto.RepositorySearchResult;
import com.dvcs.search.dto.UserSearchResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for full-text search across repositories, code, and users.
 *
 * <p>Requirement 15: Search — provides search functionality for repositories,
 * code within public repositories, and users.
 */
@Service
@Transactional(readOnly = true)
public class SearchService {

    private static final Logger log = LoggerFactory.getLogger(SearchService.class);

    private final RepoRepository repoRepository;
    private final UserRepository userRepository;
    private final GitObjectRepository gitObjectRepository;
    private final GitObjectReaderService gitObjectReaderService;

    public SearchService(RepoRepository repoRepository,
                         UserRepository userRepository,
                         GitObjectRepository gitObjectRepository,
                         GitObjectReaderService gitObjectReaderService) {
        this.repoRepository = repoRepository;
        this.userRepository = userRepository;
        this.gitObjectRepository = gitObjectRepository;
        this.gitObjectReaderService = gitObjectReaderService;
    }

    /**
     * Searches for repositories by name or description.
     *
     * <p>Only public repositories are included in the results.
     *
     * @param query    the search query string
     * @param pageable pagination parameters
     * @return page of repository search results
     */
    public Page<RepositorySearchResult> searchRepositories(String query, Pageable pageable) {
        log.debug("Searching repositories with query: {}", query);

        String pattern = "%" + query.toLowerCase() + "%";

        // Find all public repositories matching the query
        List<Repository> allRepos = repoRepository.findAll().stream()
                .filter(r -> !r.isPrivate())
                .filter(r -> matchesQuery(r, pattern))
                .collect(Collectors.toList());

        // Sort by name
        allRepos.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));

        // Build owner username map
        Map<Long, String> ownerUsernameMap = buildOwnerUsernameMap(allRepos);

        // Apply pagination manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allRepos.size());
        List<Repository> pageRepos = allRepos.subList(start, end);

        // Convert to DTOs
        List<RepositorySearchResult> results = pageRepos.stream()
                .map(r -> toRepositorySearchResult(r, ownerUsernameMap.get(r.getOwnerId())))
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, allRepos.size());
    }

    /**
     * Searches for code within public repositories.
     *
     * <p>Scans git_objects of type BLOB in public repos, reads content via
     * ObjectStoreService, and returns matches with snippets.
     *
     * @param query    the search query string
     * @param pageable pagination parameters
     * @return page of code search results
     */
    public Page<CodeSearchResult> searchCode(String query, Pageable pageable) {
        log.debug("Searching code with query: {}", query);

        // Get all public repositories
        List<Repository> publicRepos = repoRepository.findAll().stream()
                .filter(r -> !r.isPrivate())
                .collect(Collectors.toList());

        if (publicRepos.isEmpty()) {
            return Page.empty(pageable);
        }

        // Build owner username map
        Map<Long, String> ownerUsernameMap = buildOwnerUsernameMap(publicRepos);

        // Get all BLOB objects from public repos
        List<Long> publicRepoIds = publicRepos.stream()
                .map(Repository::getId)
                .collect(Collectors.toList());

        List<GitObject> blobs = gitObjectRepository.findAll().stream()
                .filter(obj -> "BLOB".equals(obj.getType()))
                .filter(obj -> publicRepoIds.contains(obj.getRepoId()))
                .collect(Collectors.toList());

        // Search through blobs
        List<CodeSearchResult> allResults = new ArrayList<>();
        for (GitObject blob : blobs) {
            try {
                Repository repo = publicRepos.stream()
                        .filter(r -> r.getId().equals(blob.getRepoId()))
                        .findFirst()
                        .orElse(null);

                if (repo == null) {
                    continue;
                }

                byte[] content = gitObjectReaderService.readBlobContent(
                        String.valueOf(blob.getRepoId()), blob.getSha());

                // Skip binary files (simple heuristic: check for null bytes)
                if (isBinary(content)) {
                    continue;
                }

                String contentStr = new String(content, StandardCharsets.UTF_8);

                // Check if content contains the query (case-insensitive)
                if (contentStr.toLowerCase().contains(query.toLowerCase())) {
                    String snippet = extractSnippet(contentStr, query);
                    String ownerUsername = ownerUsernameMap.get(repo.getOwnerId());

                    // Extract file path from stored_path (simplified)
                    String filePath = extractFilePath(blob.getStoredPath());

                    allResults.add(CodeSearchResult.builder()
                            .repoOwner(ownerUsername)
                            .repoName(repo.getName())
                            .filePath(filePath)
                            .snippet(snippet)
                            .build());
                }
            } catch (IOException e) {
                log.warn("Failed to read blob {} for repo {}: {}",
                        blob.getSha(), blob.getRepoId(), e.getMessage());
            }
        }

        // Apply pagination
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allResults.size());
        List<CodeSearchResult> pageResults = allResults.subList(start, end);

        return new PageImpl<>(pageResults, pageable, allResults.size());
    }

    /**
     * Searches for users by username or bio.
     *
     * @param query    the search query string
     * @param pageable pagination parameters
     * @return page of user search results
     */
    public Page<UserSearchResult> searchUsers(String query, Pageable pageable) {
        log.debug("Searching users with query: {}", query);

        String pattern = "%" + query.toLowerCase() + "%";

        // Find all users matching the query
        List<User> allUsers = userRepository.findAll().stream()
                .filter(u -> matchesUserQuery(u, pattern))
                .collect(Collectors.toList());

        // Sort by username
        allUsers.sort((a, b) -> a.getUsername().compareToIgnoreCase(b.getUsername()));

        // Apply pagination manually
        int start = (int) pageable.getOffset();
        int end = Math.min(start + pageable.getPageSize(), allUsers.size());
        List<User> pageUsers = allUsers.subList(start, end);

        // Convert to DTOs
        List<UserSearchResult> results = pageUsers.stream()
                .map(this::toUserSearchResult)
                .collect(Collectors.toList());

        return new PageImpl<>(results, pageable, allUsers.size());
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Checks if a repository matches the search pattern.
     */
    private boolean matchesQuery(Repository repo, String pattern) {
        String name = repo.getName() != null ? repo.getName().toLowerCase() : "";
        String desc = repo.getDescription() != null ? repo.getDescription().toLowerCase() : "";

        String queryPattern = pattern.replace("%", "");
        return name.contains(queryPattern) || desc.contains(queryPattern);
    }

    /**
     * Checks if a user matches the search pattern.
     */
    private boolean matchesUserQuery(User user, String pattern) {
        String username = user.getUsername() != null ? user.getUsername().toLowerCase() : "";
        String bio = user.getBio() != null ? user.getBio().toLowerCase() : "";

        String queryPattern = pattern.replace("%", "");
        return username.contains(queryPattern) || bio.contains(queryPattern);
    }

    /**
     * Builds a map of owner ID to username for the given repositories.
     */
    private Map<Long, String> buildOwnerUsernameMap(List<Repository> repos) {
        List<Long> ownerIds = repos.stream()
                .map(Repository::getOwnerId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, String> map = new HashMap<>();
        for (Long ownerId : ownerIds) {
            userRepository.findById(ownerId).ifPresent(user ->
                    map.put(ownerId, user.getUsername()));
        }
        return map;
    }

    /**
     * Extracts a snippet around the first occurrence of the query in the content.
     */
    private String extractSnippet(String content, String query) {
        int maxSnippetLength = 200;
        int index = content.toLowerCase().indexOf(query.toLowerCase());

        if (index == -1) {
            // Fallback: return first N characters
            return content.substring(0, Math.min(maxSnippetLength, content.length()));
        }

        // Extract context around the match
        int start = Math.max(0, index - 50);
        int end = Math.min(content.length(), index + query.length() + 150);

        String snippet = content.substring(start, end);

        // Add ellipsis if truncated
        if (start > 0) {
            snippet = "..." + snippet;
        }
        if (end < content.length()) {
            snippet = snippet + "...";
        }

        return snippet;
    }

    /**
     * Simple binary detection: checks for null bytes in the first 8000 bytes.
     */
    private boolean isBinary(byte[] content) {
        int checkLength = Math.min(8000, content.length);
        for (int i = 0; i < checkLength; i++) {
            if (content[i] == 0) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extracts a file path from the stored_path.
     * Simplified: returns the SHA as the path since we don't have tree structure here.
     */
    private String extractFilePath(String storedPath) {
        // The stored_path is typically something like "objects/ab/cdef..."
        // For search results, we'll just return a simplified representation
        if (storedPath != null && storedPath.contains("/")) {
            String[] parts = storedPath.split("/");
            if (parts.length >= 2) {
                return parts[parts.length - 2] + "/" + parts[parts.length - 1];
            }
        }
        return storedPath != null ? storedPath : "unknown";
    }

    /**
     * Converts a Repository entity to a RepositorySearchResult DTO.
     */
    private RepositorySearchResult toRepositorySearchResult(Repository repo, String ownerUsername) {
        return RepositorySearchResult.builder()
                .id(repo.getId())
                .ownerUsername(ownerUsername)
                .name(repo.getName())
                .description(repo.getDescription())
                .isPrivate(repo.isPrivate())
                .createdAt(repo.getCreatedAt())
                .build();
    }

    /**
     * Converts a User entity to a UserSearchResult DTO.
     */
    private UserSearchResult toUserSearchResult(User user) {
        return UserSearchResult.builder()
                .id(user.getId())
                .username(user.getUsername())
                .email(user.getEmail())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
