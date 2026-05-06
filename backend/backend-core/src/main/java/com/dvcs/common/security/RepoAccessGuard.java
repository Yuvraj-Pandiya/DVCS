package com.dvcs.common.security;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.repository.domain.Repository;
import com.dvcs.repository.repository.CollaboratorRepository;
import com.dvcs.repository.repository.RepoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Spring Security guard bean used in {@code @PreAuthorize} SpEL expressions on
 * Git transport and repository controller methods.
 *
 * <h2>Usage in controllers</h2>
 * <pre>{@code
 * @PreAuthorize("@repoAccessGuard.canRead(authentication, #owner, #repo)")
 * public ResponseEntity<?> infoRefs(...) { ... }
 *
 * @PreAuthorize("@repoAccessGuard.canWrite(authentication, #owner, #repo)")
 * public ResponseEntity<?> receivePack(...) { ... }
 * }</pre>
 */
@Component("repoAccessGuard")
public class RepoAccessGuard {

    private static final Logger log = LoggerFactory.getLogger(RepoAccessGuard.class);

    /** Roles that grant write access. */
    private static final List<String> WRITE_ROLES = List.of("OWNER", "WRITE");

    private final UserRepository userRepository;
    private final RepoRepository repoRepository;
    private final CollaboratorRepository collaboratorRepository;

    public RepoAccessGuard(UserRepository userRepository,
                           RepoRepository repoRepository,
                           CollaboratorRepository collaboratorRepository) {
        this.userRepository = userRepository;
        this.repoRepository = repoRepository;
        this.collaboratorRepository = collaboratorRepository;
    }

    /**
     * Returns {@code true} if the authenticated principal has at least READ access
     * to the repository identified by {@code owner}/{@code repo}.
     *
     * <p>Public repositories are accessible without authentication. Private
     * repositories require the caller to be an authenticated collaborator
     * (any role: OWNER, WRITE, or READ).
     *
     * @param authentication the current Spring Security authentication (may be
     *                       {@code null} or anonymous for public repos)
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @return {@code true} if access is permitted; {@code false} otherwise
     */
    public boolean canRead(Authentication authentication, String owner, String repo) {
        try {
            Optional<Repository> repoOpt = findRepository(owner, repo);
            if (repoOpt.isEmpty()) {
                return false;
            }
            Repository repository = repoOpt.get();

            // Public repositories are readable by anyone
            if (!repository.isPrivate()) {
                return true;
            }

            // Private repository: require authenticated collaborator
            User user = extractUser(authentication);
            if (user == null) {
                return false;
            }

            return collaboratorRepository.findByRepoIdAndUserId(repository.getId(), user.getId()).isPresent();
        } catch (Exception e) {
            log.warn("Unexpected error checking read access for {}/{}: {}", owner, repo, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Returns {@code true} if the authenticated principal has WRITE or OWNER access
     * to the repository identified by {@code owner}/{@code repo}.
     *
     * @param authentication the current Spring Security authentication; must not be
     *                       {@code null} and must be authenticated for write access
     * @param owner          the repository owner's username
     * @param repo           the repository name
     * @return {@code true} if write access is permitted; {@code false} otherwise
     */
    public boolean canWrite(Authentication authentication, String owner, String repo) {
        try {
            // Write always requires authentication
            User user = extractUser(authentication);
            if (user == null) {
                return false;
            }

            Optional<Repository> repoOpt = findRepository(owner, repo);
            if (repoOpt.isEmpty()) {
                return false;
            }
            Repository repository = repoOpt.get();

            return collaboratorRepository.existsByRepoIdAndUserIdAndRoleIn(
                    repository.getId(), user.getId(), WRITE_ROLES);
        } catch (Exception e) {
            log.warn("Unexpected error checking write access for {}/{}: {}", owner, repo, e.getMessage(), e);
            return false;
        }
    }

    /**
     * Extracts the {@link User} entity from the given {@link Authentication} principal.
     *
     * <p>The {@link JwtAuthenticationFilter} sets the principal to a {@link User}
     * instance, so this cast is safe when the filter has run successfully.
     *
     * @param authentication the current Spring Security authentication
     * @return the authenticated {@link User}, or {@code null} if not available
     */
    public static User extractUser(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof User user) {
            return user;
        }
        return null;
    }

    /**
     * Looks up a repository by owner username and repository name.
     *
     * @param owner    the owner's username
     * @param repoName the repository name
     * @return an {@link Optional} containing the repository, or empty if not found
     */
    private Optional<Repository> findRepository(String owner, String repoName) {
        Optional<User> ownerOpt = userRepository.findByUsername(owner);
        if (ownerOpt.isEmpty()) {
            return Optional.empty();
        }
        return repoRepository.findByOwnerIdAndName(ownerOpt.get().getId(), repoName);
    }
}
