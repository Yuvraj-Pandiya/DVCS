package com.dvcs.git.object;

import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Abstract base class for all Git objects in the content-addressable object store.
 *
 * <p>Every Git object is uniquely identified by its SHA-256 hash (64 hex characters)
 * and carries a type discriminator. Subclasses must implement {@link #serialize()}
 * to produce the canonical byte encoding used for storage and hashing.
 *
 * <p>Requirement 4: Git Object Storage Engine — SHA-256 hash as unique key,
 * content-addressable storage.
 */
public abstract class GitObject {

    /** Pattern that a valid SHA-256 hex string must match (exactly 64 lowercase hex chars). */
    private static final Pattern SHA_PATTERN = Pattern.compile("^[0-9a-f]{64}$");

    /**
     * The SHA-256 hex digest of this object's canonical byte encoding.
     * Always exactly 64 lowercase hexadecimal characters.
     */
    private final String sha;

    /**
     * The type of this Git object (BLOB, TREE, or COMMIT).
     */
    private final ObjectType type;

    /**
     * Constructs a {@code GitObject} with the given SHA-256 hash and type.
     *
     * @param sha  the 64-character lowercase hex SHA-256 digest; must not be {@code null}
     *             and must match {@code [0-9a-f]{64}}
     * @param type the object type; must not be {@code null}
     * @throws IllegalArgumentException if {@code sha} is not a valid 64-char hex string
     * @throws NullPointerException     if {@code sha} or {@code type} is {@code null}
     */
    protected GitObject(String sha, ObjectType type) {
        Objects.requireNonNull(sha, "sha must not be null");
        Objects.requireNonNull(type, "type must not be null");
        if (!SHA_PATTERN.matcher(sha).matches()) {
            throw new IllegalArgumentException(
                    "sha must be a 64-character lowercase hex string, got: " + sha);
        }
        this.sha = sha;
        this.type = type;
    }

    /**
     * Returns the SHA-256 hex digest of this object.
     *
     * @return 64-character lowercase hex string
     */
    public String getSha() {
        return sha;
    }

    /**
     * Returns the type of this Git object.
     *
     * @return one of {@link ObjectType#BLOB}, {@link ObjectType#TREE},
     *         or {@link ObjectType#COMMIT}
     */
    public ObjectType getType() {
        return type;
    }

    /**
     * Produces the canonical byte encoding of this object.
     *
     * <p>The returned bytes are the exact content that was (or will be) SHA-256 hashed
     * to derive {@link #getSha()}. Implementations must be deterministic and
     * produce the same bytes for the same logical content.
     *
     * @return canonical byte array representation of this object; never {@code null}
     */
    public abstract byte[] serialize();

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof GitObject)) return false;
        GitObject other = (GitObject) o;
        return sha.equals(other.sha) && type == other.type;
    }

    @Override
    public int hashCode() {
        return Objects.hash(sha, type);
    }

    @Override
    public String toString() {
        return type.name().toLowerCase() + ":" + sha;
    }
}
