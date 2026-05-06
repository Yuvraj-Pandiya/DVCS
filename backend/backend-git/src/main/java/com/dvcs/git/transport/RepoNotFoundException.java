package com.dvcs.git.transport;

/**
 * Thrown when a repository cannot be found by owner and name, or when the
 * caller does not have permission to access it.
 *
 * <p>Mapped to HTTP 404 by the global exception handler so that the existence
 * of private repositories is not disclosed to unauthorized callers.
 */
public class RepoNotFoundException extends RuntimeException {

    /**
     * Constructs a {@code RepoNotFoundException} with a descriptive message.
     *
     * @param owner    the repository owner username
     * @param repoName the repository name
     */
    public RepoNotFoundException(String owner, String repoName) {
        super("Repository '" + owner + "/" + repoName + "' does not exist or is not accessible.");
    }
}
