package com.dvcs.git.pack;

import com.dvcs.git.object.ObjectType;
import com.dvcs.git.storage.ObjectStoreService;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.zip.Deflater;
import java.util.zip.DeflaterOutputStream;

/**
 * Encodes a list of Git object SHAs into a binary pack-file stream.
 *
 * <h2>Pack-file format</h2>
 * <pre>
 *   4 bytes  — magic: 'P' 'A' 'C' 'K'
 *   4 bytes  — version: 2 (big-endian)
 *   4 bytes  — object count (big-endian)
 *   per object:
 *     variable — type + size header (variable-length encoding, see below)
 *     variable — zlib-deflated object bytes
 *   32 bytes — SHA-256 of all preceding bytes (trailer)
 * </pre>
 *
 * <h2>Type + size variable-length encoding</h2>
 * The first byte encodes:
 * <ul>
 *   <li>bit 7 (MSB): more-bytes flag (1 = more bytes follow)</li>
 *   <li>bits 6–4: object type (1=COMMIT, 2=TREE, 3=BLOB)</li>
 *   <li>bits 3–0: low 4 bits of the uncompressed size</li>
 * </ul>
 * Subsequent bytes (if more-bytes flag is set):
 * <ul>
 *   <li>bit 7: more-bytes flag</li>
 *   <li>bits 6–0: next 7 bits of the size</li>
 * </ul>
 *
 * <p>Requirement 4 / Req 6: Pack-File Transfer Format.
 */
@Component
public class PackFileEncoder {

    /** Pack-file magic bytes: ASCII "PACK". */
    static final byte[] PACK_MAGIC = {'P', 'A', 'C', 'K'};

    /** Pack-file format version. */
    static final int PACK_VERSION = 2;

    /** Type code for COMMIT objects in the pack header. */
    static final int TYPE_COMMIT = 1;

    /** Type code for TREE objects in the pack header. */
    static final int TYPE_TREE = 2;

    /** Type code for BLOB objects in the pack header. */
    static final int TYPE_BLOB = 3;

    private final ObjectStoreService objectStoreService;

    /**
     * Constructs a {@code PackFileEncoder}.
     *
     * @param objectStoreService the service used to read raw object bytes; must not be {@code null}
     */
    public PackFileEncoder(ObjectStoreService objectStoreService) {
        this.objectStoreService = Objects.requireNonNull(objectStoreService,
                "objectStoreService must not be null");
    }

    /**
     * Encodes the specified objects into a pack-file byte array.
     *
     * <p>For each SHA in {@code shas}, the raw bytes are read from the
     * {@link ObjectStoreService}, the object type is inferred from the header prefix,
     * and the bytes are written into the pack stream with a variable-length type+size
     * header followed by zlib-deflated content.
     *
     * <p>A SHA-256 digest of all preceding bytes is appended as a 32-byte trailer.
     *
     * @param repoId the repository identifier; must not be {@code null}
     * @param shas   the list of SHA-256 hex digests to include; must not be {@code null}
     * @return the complete pack-file as a byte array
     * @throws IOException          if reading any object from the store fails
     * @throws NullPointerException if {@code repoId} or {@code shas} is {@code null}
     */
    public byte[] encode(String repoId, List<String> shas) throws IOException {
        Objects.requireNonNull(repoId, "repoId must not be null");
        Objects.requireNonNull(shas,   "shas must not be null");

        ByteArrayOutputStream packBody = new ByteArrayOutputStream();

        // Write magic + version + object count (12 bytes)
        packBody.write(PACK_MAGIC);
        packBody.write(toBeInt(PACK_VERSION));
        packBody.write(toBeInt(shas.size()));

        // Write each object
        for (String sha : shas) {
            byte[] rawBytes = objectStoreService.readObject(repoId, sha);
            int typeCode = detectTypeCode(rawBytes);
            int uncompressedSize = rawBytes.length;

            // Write variable-length type+size header
            packBody.write(encodeTypeSize(typeCode, uncompressedSize));

            // Write zlib-deflated object bytes
            packBody.write(deflate(rawBytes));
        }

        byte[] bodyBytes = packBody.toByteArray();

        // Compute SHA-256 trailer over all preceding bytes
        byte[] trailer = sha256(bodyBytes);

        // Assemble final pack
        ByteArrayOutputStream result = new ByteArrayOutputStream(bodyBytes.length + 32);
        result.write(bodyBytes);
        result.write(trailer);
        return result.toByteArray();
    }

    // -------------------------------------------------------------------------
    // Package-private helpers (used by tests and decoder)
    // -------------------------------------------------------------------------

    /**
     * Encodes a type code and uncompressed size into the variable-length header bytes.
     *
     * <p>First byte layout: {@code [more][type(3)][size-low(4)]}.
     * Subsequent bytes: {@code [more][size-bits(7)]}.
     *
     * @param typeCode         object type code (1=COMMIT, 2=TREE, 3=BLOB)
     * @param uncompressedSize the uncompressed byte length of the object
     * @return the variable-length header bytes
     */
    static byte[] encodeTypeSize(int typeCode, int uncompressedSize) {
        ByteArrayOutputStream out = new ByteArrayOutputStream(4);

        // First byte: [more][type(3)][size-low(4)]
        int sizeLow = uncompressedSize & 0x0F;
        int sizeRemainder = uncompressedSize >>> 4;
        int firstByte = (typeCode & 0x07) << 4 | sizeLow;
        if (sizeRemainder > 0) {
            firstByte |= 0x80; // set more-bytes flag
        }
        out.write(firstByte);

        // Subsequent bytes: [more][size-bits(7)]
        while (sizeRemainder > 0) {
            int b = sizeRemainder & 0x7F;
            sizeRemainder >>>= 7;
            if (sizeRemainder > 0) {
                b |= 0x80; // more bytes follow
            }
            out.write(b);
        }

        return out.toByteArray();
    }

    /**
     * Converts a 32-bit integer to a 4-byte big-endian array.
     *
     * @param value the integer to convert
     * @return 4-byte big-endian representation
     */
    static byte[] toBeInt(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte)  value
        };
    }

    /**
     * Computes the SHA-256 digest of the given bytes.
     *
     * @param data the bytes to hash
     * @return 32-byte SHA-256 digest
     */
    static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Infers the pack type code from the raw object bytes by inspecting the
     * canonical header prefix ({@code "blob "}, {@code "tree "}, {@code "commit "}).
     *
     * @param rawBytes the raw serialized object bytes
     * @return type code: {@link #TYPE_BLOB}, {@link #TYPE_TREE}, or {@link #TYPE_COMMIT}
     * @throws IllegalArgumentException if the type cannot be determined
     */
    private static int detectTypeCode(byte[] rawBytes) {
        if (startsWith(rawBytes, "blob "))   return TYPE_BLOB;
        if (startsWith(rawBytes, "tree "))   return TYPE_TREE;
        if (startsWith(rawBytes, "commit ")) return TYPE_COMMIT;
        throw new IllegalArgumentException(
                "Cannot determine object type from raw bytes (unknown header prefix)");
    }

    /**
     * Returns {@code true} if {@code bytes} starts with the UTF-8 encoding of {@code prefix}.
     */
    private static boolean startsWith(byte[] bytes, String prefix) {
        byte[] prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        if (bytes.length < prefixBytes.length) return false;
        for (int i = 0; i < prefixBytes.length; i++) {
            if (bytes[i] != prefixBytes[i]) return false;
        }
        return true;
    }

    /**
     * Compresses {@code data} using zlib deflate (default compression level).
     *
     * @param data the bytes to compress
     * @return zlib-compressed bytes
     * @throws IOException if compression fails
     */
    private static byte[] deflate(byte[] data) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (DeflaterOutputStream deflater =
                     new DeflaterOutputStream(out, new Deflater(Deflater.DEFAULT_COMPRESSION))) {
            deflater.write(data);
        }
        return out.toByteArray();
    }

    /**
     * Maps an {@link ObjectType} to its pack type code.
     *
     * @param type the object type
     * @return pack type code
     */
    static int typeCode(ObjectType type) {
        return switch (type) {
            case COMMIT -> TYPE_COMMIT;
            case TREE   -> TYPE_TREE;
            case BLOB   -> TYPE_BLOB;
        };
    }
}
