package com.dvcs.git.storage;

/**
 * Thrown by {@link ObjectStoreBackend#read} and {@link ObjectStoreBackend#size}
 * when no object exists for the requested {@code repoId}/{@code sha} key.
 *
 * <p>Maps to HTTP 404 at the REST layer (Req 7 — object reads must return stored
 * bytes or 404 if not found).
 */
public class ObjectNotFoundException extends RuntimeException {

    private final String repoId;
    private final String sha;

    /**
     * Constructs an {@code ObjectNotFoundException} for the given key.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest that was not found
     */
    public ObjectNotFoundException(String repoId, String sha) {
        super(String.format("Object not found: repoId=%s sha=%s", repoId, sha));
        this.repoId = repoId;
        this.sha = sha;
    }

    /**
     * Constructs an {@code ObjectNotFoundException} with a custom message.
     *
     * @param repoId  repository identifier
     * @param sha     64-char lowercase hex SHA-256 digest that was not found
     * @param message additional context
     */
    public ObjectNotFoundException(String repoId, String sha, String message) {
        super(message);
        this.repoId = repoId;
        this.sha = sha;
    }

    /** @return the repository identifier associated with the missing object */
    public String getRepoId() {
        return repoId;
    }

    /** @return the SHA-256 hex digest of the missing object */
    public String getSha() {
        return sha;
    }
}
