package com.dvcs.repository.service;

import java.io.IOException;

/**
 * Service interface for writing Git objects to the object store.
 *
 * <p>Requirement 4: Git Object Storage Engine.
 */
public interface GitObjectWriterService {

    /**
     * Creates an initial commit for a repository with a default README.md.
     *
     * @param repoId        the repository ID string
     * @param ownerUsername the username of the repository owner
     * @param repoName      the name of the repository
     * @return the SHA of the initial commit
     * @throws IOException if writing to the object store fails
     */
    String initializeRepo(String repoId, String ownerUsername, String repoName) throws IOException;
}
