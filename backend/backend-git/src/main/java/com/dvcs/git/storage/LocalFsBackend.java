package com.dvcs.git.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * Local filesystem implementation of {@link ObjectStoreBackend}.
 *
 * <p>Objects are stored at:
 * <pre>{@code ${storage.root}/{repoId}/objects/{sha[0..1]}/{sha[2..]}}</pre>
 * mirroring Git's loose-object layout (first 2 hex chars as directory prefix,
 * remaining 62 chars as the filename).
 *
 * <p>Writes are performed atomically: bytes are first written to a sibling
 * {@code .tmp} file and then moved into place with
 * {@link StandardCopyOption#ATOMIC_MOVE} (best-effort; falls back to a
 * regular move on platforms that do not support atomic rename).
 */
@Component
@ConditionalOnProperty(name = "storage.backend", havingValue = "local", matchIfMissing = true)
public class LocalFsBackend implements ObjectStoreBackend {

    private final Path storageRoot;

    /**
     * Creates a {@code LocalFsBackend} whose root directory is taken from the
     * {@code storage.root} Spring property (defaults to {@code ./data}).
     *
     * @param storageRoot path to the root storage directory
     */
    public LocalFsBackend(@Value("${storage.root:./data}") String storageRoot) {
        this.storageRoot = Paths.get(storageRoot);
    }

    // -------------------------------------------------------------------------
    // ObjectStoreBackend implementation
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Parent directories are created automatically. The write is performed
     * atomically via a temp-file-then-move strategy.
     */
    @Override
    public void write(String repoId, String sha, byte[] data) throws IOException {
        Path target = objectPath(repoId, sha);
        Files.createDirectories(target.getParent());

        // Write to a sibling temp file first, then atomically rename.
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.write(tmp, data, StandardOpenOption.CREATE, StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            // ATOMIC_MOVE is best-effort; retry without it if the filesystem
            // does not support it (e.g. cross-device moves).
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Clean up the temp file and rethrow the original error.
                Files.deleteIfExists(tmp);
                throw e;
            }
        }
    }

    /**
     * {@inheritDoc}
     *
     * @throws ObjectNotFoundException if no file exists for the given key
     */
    @Override
    public byte[] read(String repoId, String sha) throws IOException {
        Path target = objectPath(repoId, sha);
        if (!Files.exists(target)) {
            throw new ObjectNotFoundException(repoId, sha);
        }
        return Files.readAllBytes(target);
    }

    /** {@inheritDoc} */
    @Override
    public boolean exists(String repoId, String sha) {
        return Files.exists(objectPath(repoId, sha));
    }

    /**
     * {@inheritDoc}
     *
     * <p>If the object does not exist this method completes without error.
     */
    @Override
    public void delete(String repoId, String sha) throws IOException {
        Files.deleteIfExists(objectPath(repoId, sha));
    }

    /**
     * {@inheritDoc}
     *
     * @throws ObjectNotFoundException if no file exists for the given key
     */
    @Override
    public long size(String repoId, String sha) throws IOException {
        Path target = objectPath(repoId, sha);
        if (!Files.exists(target)) {
            throw new ObjectNotFoundException(repoId, sha);
        }
        return Files.size(target);
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves the filesystem path for a given {@code repoId} / {@code sha} pair.
     *
     * <p>Layout: {@code <storageRoot>/<repoId>/objects/<sha[0..1]>/<sha[2..]>}
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return absolute (or relative-to-CWD) {@link Path} for the object file
     */
    Path objectPath(String repoId, String sha) {
        String prefix = sha.substring(0, 2);
        String rest   = sha.substring(2);
        return storageRoot.resolve(repoId).resolve("objects").resolve(prefix).resolve(rest);
    }
}
