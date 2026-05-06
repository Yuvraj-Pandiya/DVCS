package com.dvcs.git.storage;

import java.io.IOException;

/**
 * Backend interface for the content-addressable object store.
 *
 * <p>Implementations must be interchangeable without changing application code
 * (Req 4 — S3-compatible interface). Two implementations are provided:
 * {@code LocalFsBackend} (local filesystem) and {@code S3Backend} (MinIO / AWS S3).
 *
 * <p>All methods are keyed by {@code repoId} (repository identifier) and {@code sha}
 * (64-character lowercase hex SHA-256 digest of the object content).
 */
public interface ObjectStoreBackend {

    /**
     * Writes {@code data} to the store under the given {@code repoId} and {@code sha}.
     * If an object with the same key already exists it may be overwritten.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @param data   raw object bytes
     * @throws IOException if the write fails
     */
    void write(String repoId, String sha, byte[] data) throws IOException;

    /**
     * Reads and returns the raw bytes stored under {@code repoId}/{@code sha}.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return stored bytes
     * @throws ObjectNotFoundException if no object exists for the given key (Req 7 — 404 if not found)
     * @throws IOException             if the read fails for any other reason
     */
    byte[] read(String repoId, String sha) throws IOException;

    /**
     * Returns {@code true} if an object exists for the given {@code repoId}/{@code sha},
     * {@code false} otherwise.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return {@code true} if the object exists
     */
    boolean exists(String repoId, String sha);

    /**
     * Deletes the object stored under {@code repoId}/{@code sha}.
     * If the object does not exist this method completes without error.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @throws IOException if the deletion fails
     */
    void delete(String repoId, String sha) throws IOException;

    /**
     * Returns the size in bytes of the object stored under {@code repoId}/{@code sha}.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return size in bytes
     * @throws ObjectNotFoundException if no object exists for the given key
     * @throws IOException             if the size query fails for any other reason
     */
    long size(String repoId, String sha) throws IOException;
}
