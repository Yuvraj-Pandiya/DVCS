package com.dvcs.git.object;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Represents a Git blob object — raw file content stored in the content-addressable
 * object store.
 *
 * <p>Serialization format: {@code blob {size}\0{raw bytes}}, where {@code {size}} is
 * the byte length of the content and {@code \0} is the ASCII NUL character.
 *
 * <p>The SHA-256 hash is computed from the serialized bytes and set automatically
 * in the constructor.
 *
 * <p>Requirement 4: Git Object Storage Engine — blob serialization and SHA-256 hashing.
 */
public class BlobObject extends GitObject {

    /** The raw file content stored in this blob. */
    private final byte[] content;

    /**
     * Constructs a {@code BlobObject} from raw content bytes.
     *
     * <p>The SHA-256 hash is computed from the full serialized form
     * ({@code "blob {size}\0"} header + content) and stored in the parent class.
     *
     * @param content the raw file content; must not be {@code null}
     * @throws NullPointerException if {@code content} is {@code null}
     */
    public BlobObject(byte[] content) {
        super(computeSha(Objects.requireNonNull(content, "content must not be null")),
              ObjectType.BLOB);
        this.content = Arrays.copyOf(content, content.length);
    }

    /**
     * Returns the canonical byte encoding of this blob.
     *
     * <p>Format: {@code "blob {size}\0"} (UTF-8) followed by the raw content bytes,
     * where {@code {size}} is the byte length of the content.
     *
     * @return canonical byte array; never {@code null}
     */
    @Override
    public byte[] serialize() {
        byte[] header = buildHeader(content.length);
        byte[] result = new byte[header.length + content.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(content, 0, result, header.length, content.length);
        return result;
    }

    /**
     * Returns a defensive copy of the raw content bytes.
     *
     * @return copy of the content byte array; never {@code null}
     */
    public byte[] getContent() {
        return Arrays.copyOf(content, content.length);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the blob header bytes: {@code "blob {size}\0"} encoded as UTF-8.
     *
     * @param size the byte length of the content
     * @return header bytes
     */
    private static byte[] buildHeader(int size) {
        String headerStr = "blob " + size + "\0";
        return headerStr.getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Computes the SHA-256 hex digest of the serialized blob
     * ({@code "blob {size}\0"} + content).
     *
     * @param content the raw content bytes
     * @return 64-character lowercase hex SHA-256 string
     */
    private static String computeSha(byte[] content) {
        byte[] header = buildHeader(content.length);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(header);
            digest.update(content);
            byte[] hashBytes = digest.digest();
            return bytesToHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    /**
     * Converts a byte array to a lowercase hexadecimal string.
     *
     * @param bytes the bytes to convert
     * @return lowercase hex string
     */
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
