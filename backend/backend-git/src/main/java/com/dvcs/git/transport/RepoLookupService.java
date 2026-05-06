package com.dvcs.git.transport;

/**
 * Service interface for resolving a repository's internal ID from its owner
 * username and repository name.
 *
 * <p>This interface decouples {@link GitTransportController} from the concrete
 * JPA repository layer, which is implemented in task 7.1. A stub implementation
 * ({@link StubRepoLookupService}) is provided here so that the transport layer
 * compiles and can be wired up before the full repository module is complete.
 */
public interface RepoLookupService {

    /**
     * Resolves the internal repository ID for the given owner and repository name.
     *
     * @param owner    the username of the repository owner
     * @param repoName the repository name
     * @return the internal {@code Long} repository ID
     * @throws RepoNotFoundException if no repository with the given owner/name exists
     *                               or is not accessible to the caller
     */
    Long resolveRepoId(String owner, String repoName);
}
