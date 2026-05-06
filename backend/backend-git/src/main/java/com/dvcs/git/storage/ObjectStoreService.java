package com.dvcs.git.storage;

import com.dvcs.git.object.GitObject;
import com.dvcs.git.object.SHA256Util;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Duration;
import java.util.Base64;
import java.util.Objects;

/**
 * Facade service over {@link ObjectStoreBackend} that adds Redis caching.
 *
 * <p>The active backend is selected by the {@code storage.backend} property
 * (env var {@code STORAGE_BACKEND}):
 * <ul>
 *   <li>{@code s3} → {@link S3Backend} (activated via {@code @ConditionalOnProperty})</li>
 *   <li>anything else (default) → {@link LocalFsBackend} (activated via
 *       {@code @ConditionalOnMissingBean})</li>
 * </ul>
 *
 * <p>Redis key schema: {@code blob:{repoId}:{sha}} → Base64-encoded raw bytes, TTL 3600 s.
 * Objects are immutable once written, so the cache never needs invalidation.
 *
 * <p>Requirement 4: Git Object Storage Engine — content-addressable storage with
 * Redis caching layer.
 */
@Service
public class ObjectStoreService {

    /** Redis TTL for cached objects (immutable, so no invalidation needed). */
    private static final Duration CACHE_TTL = Duration.ofSeconds(3600);

    private final ObjectStoreBackend backend;
    private final StringRedisTemplate redisTemplate;

    /**
     * Constructs an {@code ObjectStoreService}.
     *
     * <p>Spring injects whichever {@link ObjectStoreBackend} implementation is
     * active: {@link S3Backend} when {@code storage.backend=s3}, otherwise
     * {@link LocalFsBackend}.
     *
     * @param backend       the active storage backend
     * @param redisTemplate Spring Data Redis template for string-valued keys
     */
    public ObjectStoreService(ObjectStoreBackend backend, StringRedisTemplate redisTemplate) {
        this.backend = Objects.requireNonNull(backend, "backend must not be null");
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate must not be null");
    }

    /**
     * Writes a {@link GitObject} to the backend and caches it in Redis.
     *
     * <ol>
     *   <li>Calls {@link GitObject#serialize()} to obtain the canonical bytes.</li>
     *   <li>Computes the SHA-256 hex digest via {@link SHA256Util#computeHex(byte[])}.</li>
     *   <li>Persists the bytes to the backend via {@link ObjectStoreBackend#write}.</li>
     *   <li>Caches the Base64-encoded bytes in Redis at key
     *       {@code blob:{repoId}:{sha}} with a TTL of 3600 seconds.</li>
     * </ol>
     *
     * @param repoId    repository identifier; must not be {@code null}
     * @param gitObject the object to store; must not be {@code null}
     * @return the 64-character lowercase hex SHA-256 digest of the object
     * @throws IOException          if the backend write fails
     * @throws NullPointerException if {@code repoId} or {@code gitObject} is {@code null}
     */
    public String writeObject(String repoId, GitObject gitObject) throws IOException {
        Objects.requireNonNull(repoId, "repoId must not be null");
        Objects.requireNonNull(gitObject, "gitObject must not be null");

        byte[] bytes = gitObject.serialize();
        String sha = SHA256Util.computeHex(bytes);

        backend.write(repoId, sha, bytes);

        String cacheKey = cacheKey(repoId, sha);
        String encoded = Base64.getEncoder().encodeToString(bytes);
        redisTemplate.opsForValue().set(cacheKey, encoded, CACHE_TTL);

        return sha;
    }

    /**
     * Reads raw object bytes for the given {@code repoId} and {@code sha}.
     *
     * <ol>
     *   <li>Validates that {@code sha} is non-empty and contains no path-traversal
     *       sequences ({@code ../} or {@code ..\}).</li>
     *   <li>Checks Redis at key {@code blob:{repoId}:{sha}}.</li>
     *   <li>On cache hit: decodes from Base64 and returns the bytes.</li>
     *   <li>On cache miss: reads from the backend, verifies integrity via
     *       {@link SHA256Util#verifyIntegrity}, caches the result, and returns
     *       the bytes.</li>
     * </ol>
     *
     * @param repoId repository identifier; must not be {@code null}
     * @param sha    64-character lowercase hex SHA-256 digest; must not be
     *               {@code null} or empty, and must not contain path-traversal sequences
     * @return raw object bytes
     * @throws IllegalArgumentException if {@code sha} is null/empty or contains
     *                                  path-traversal sequences
     * @throws ObjectNotFoundException  if no object exists for the given key
     * @throws IOException              if the backend read fails
     */
    public byte[] readObject(String repoId, String sha) throws IOException {
        Objects.requireNonNull(repoId, "repoId must not be null");
        validateSha(sha);

        String cacheKey = cacheKey(repoId, sha);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return Base64.getDecoder().decode(cached);
        }

        byte[] bytes = backend.read(repoId, sha);
        SHA256Util.verifyIntegrity(sha, bytes);

        String encoded = Base64.getEncoder().encodeToString(bytes);
        redisTemplate.opsForValue().set(cacheKey, encoded, CACHE_TTL);

        return bytes;
    }

    // -------------------------------------------------------------------------
    // Internal helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the Redis cache key for a given {@code repoId} / {@code sha} pair.
     *
     * @param repoId repository identifier
     * @param sha    64-char lowercase hex SHA-256 digest
     * @return Redis key string in the form {@code blob:{repoId}:{sha}}
     */
    private static String cacheKey(String repoId, String sha) {
        return "blob:" + repoId + ":" + sha;
    }

    /**
     * Validates that {@code sha} is non-null, non-empty, and does not contain
     * path-traversal sequences.
     *
     * @param sha the SHA string to validate
     * @throws IllegalArgumentException if validation fails
     */
    private static void validateSha(String sha) {
        if (sha == null || sha.isEmpty()) {
            throw new IllegalArgumentException("sha must not be null or empty");
        }
        if (sha.contains("../") || sha.contains("..\\")) {
            throw new IllegalArgumentException(
                    "sha must not contain path-traversal sequences: " + sha);
        }
    }
}
