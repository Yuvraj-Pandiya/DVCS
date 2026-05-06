package com.dvcs.git.pack;

import com.dvcs.git.object.ObjectType;

import java.util.Arrays;
import java.util.Objects;

/**
 * A decoded Git object as returned by {@link PackFileDecoder}.
 *
 * <p>Holds the object type, its SHA-256 hex digest (computed from the raw bytes),
 * and the raw serialized bytes (the canonical form used for hashing and storage).
 *
 * <p>Requirement 4 / Req 6: Pack-File Transfer Format — decoded object representation.
 *
 * @param type the object type (BLOB, TREE, or COMMIT)
 * @param sha  the 64-character lowercase hex SHA-256 digest of {@code data}
 * @param data the raw serialized bytes of the object (canonical form)
 */
public record RawObject(ObjectType type, String sha, byte[] data) {

    /**
     * Compact canonical constructor — validates non-null fields and defensively copies
     * the {@code data} array.
     *
     * @throws NullPointerException if any parameter is {@code null}
     */
    public RawObject {
        Objects.requireNonNull(type, "type must not be null");
        Objects.requireNonNull(sha,  "sha must not be null");
        Objects.requireNonNull(data, "data must not be null");
        data = Arrays.copyOf(data, data.length);
    }

    /**
     * Returns a defensive copy of the raw object bytes.
     *
     * @return copy of the data byte array; never {@code null}
     */
    @Override
    public byte[] data() {
        return Arrays.copyOf(data, data.length);
    }
}
