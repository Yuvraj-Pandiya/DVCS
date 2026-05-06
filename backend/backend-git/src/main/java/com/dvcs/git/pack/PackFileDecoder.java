package com.dvcs.git.pack;

import com.dvcs.git.object.ObjectType;
import com.dvcs.git.object.SHA256Util;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes a binary pack-file stream into a list of {@link RawObject} instances.
 *
 * <h2>Pack-file format (expected)</h2>
 * <pre>
 *   4 bytes  — magic: 'P' 'A' 'C' 'K'
 *   4 bytes  — version: 2 (big-endian)
 *   4 bytes  — object count (big-endian)
 *   per object:
 *     variable — type + size header (variable-length encoding)
 *     variable — zlib-deflated object bytes
 *   32 bytes — SHA-256 of all preceding bytes (trailer)
 * </pre>
 *
 * <p>The decoder reads the entire stream into memory, verifies the SHA-256 trailer,
 * then parses each object entry. Each decoded object's SHA-256 is computed from its
 * raw bytes and returned in the {@link RawObject}.
 *
 * <p>Requirement 4 / Req 6: Pack-File Transfer Format.
 */
@Component
public class PackFileDecoder {

    /** Minimum pack size: 4 (magic) + 4 (version) + 4 (count) + 32 (trailer) = 44 bytes. */
    private static final int MIN_PACK_SIZE = 44;

    /** Size of the SHA-256 trailer in bytes. */
    private static final int TRAILER_SIZE = 32;

    /**
     * Decodes a pack-file stream into a list of raw Git objects.
     *
     * <ol>
     *   <li>Reads the entire stream into a byte array.</li>
     *   <li>Validates the 4-byte magic ({@code PACK}) and 4-byte version ({@code 2}).</li>
     *   <li>Reads the 4-byte object count.</li>
     *   <li>Verifies the 32-byte SHA-256 trailer against all preceding bytes.</li>
     *   <li>For each object: parses the variable-length type+size header, inflates the
     *       zlib-compressed data, computes the SHA-256 of the inflated bytes, and
     *       constructs a {@link RawObject}.</li>
     * </ol>
     *
     * @param packStream the input stream containing the pack-file bytes; must not be {@code null}
     * @return list of decoded {@link RawObject} instances in pack order
     * @throws IOException             if reading the stream fails
     * @throws PackIntegrityException  if the magic, version, or SHA-256 trailer is invalid
     * @throws NullPointerException    if {@code packStream} is {@code null}
     */
    public List<RawObject> decode(InputStream packStream) throws IOException {
        if (packStream == null) {
            throw new NullPointerException("packStream must not be null");
        }

        // Read entire stream into memory
        byte[] packBytes = readAllBytes(packStream);

        if (packBytes.length < MIN_PACK_SIZE) {
            throw new PackIntegrityException(
                    "Pack data too short: " + packBytes.length + " bytes (minimum " + MIN_PACK_SIZE + ")");
        }

        // Validate magic bytes: 'P' 'A' 'C' 'K'
        if (packBytes[0] != 'P' || packBytes[1] != 'A' ||
            packBytes[2] != 'C' || packBytes[3] != 'K') {
            throw new PackIntegrityException("Invalid pack magic bytes");
        }

        // Validate version: must be 2
        int version = readBeInt(packBytes, 4);
        if (version != PackFileEncoder.PACK_VERSION) {
            throw new PackIntegrityException(
                    "Unsupported pack version: " + version + " (expected 2)");
        }

        // Read object count
        int objectCount = readBeInt(packBytes, 8);

        // Verify SHA-256 trailer
        // The trailer covers all bytes except the last 32
        int bodyLength = packBytes.length - TRAILER_SIZE;
        byte[] bodyBytes  = Arrays.copyOf(packBytes, bodyLength);
        byte[] storedHash = Arrays.copyOfRange(packBytes, bodyLength, packBytes.length);
        byte[] computed   = sha256(bodyBytes);

        if (!Arrays.equals(computed, storedHash)) {
            throw new PackIntegrityException(
                    "Pack SHA-256 trailer mismatch: data may be corrupted or tampered");
        }

        // Parse objects starting at offset 12 (after magic + version + count)
        List<RawObject> objects = new ArrayList<>(objectCount);
        int offset = 12;

        for (int i = 0; i < objectCount; i++) {
            // Parse variable-length type+size header
            TypeSizeResult tsr = decodeTypeSize(bodyBytes, offset);
            offset = tsr.nextOffset;

            // Inflate zlib-compressed data
            // We wrap the remaining body bytes in an InflaterInputStream
            byte[] remaining = Arrays.copyOfRange(bodyBytes, offset, bodyLength);
            InflaterResult ir = inflate(remaining, tsr.uncompressedSize);
            offset += ir.compressedBytesConsumed;

            // Compute SHA-256 of the inflated (raw) bytes
            String sha = SHA256Util.computeHex(ir.data);

            objects.add(new RawObject(tsr.type, sha, ir.data));
        }

        return objects;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Reads all bytes from an {@link InputStream} into a byte array.
     *
     * @param in the input stream
     * @return all bytes read
     * @throws IOException if reading fails
     */
    private static byte[] readAllBytes(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Reads a 4-byte big-endian integer from {@code data} at {@code offset}.
     *
     * @param data   the byte array
     * @param offset the starting offset
     * @return the integer value
     */
    private static int readBeInt(byte[] data, int offset) {
        return ((data[offset]     & 0xFF) << 24)
             | ((data[offset + 1] & 0xFF) << 16)
             | ((data[offset + 2] & 0xFF) << 8)
             |  (data[offset + 3] & 0xFF);
    }

    /**
     * Decodes the variable-length type+size header starting at {@code offset} in {@code data}.
     *
     * <p>First byte layout: {@code [more][type(3)][size-low(4)]}.
     * Subsequent bytes: {@code [more][size-bits(7)]}.
     *
     * @param data   the pack body bytes
     * @param offset the offset of the first header byte
     * @return a {@link TypeSizeResult} containing the type, uncompressed size, and next offset
     */
    private static TypeSizeResult decodeTypeSize(byte[] data, int offset) {
        int b = data[offset++] & 0xFF;

        // Extract type from bits 6–4 of the first byte
        int typeCode = (b >> 4) & 0x07;

        // Extract low 4 bits of size
        long size = b & 0x0F;
        int shift = 4;

        // Read continuation bytes while more-bytes flag is set
        while ((b & 0x80) != 0) {
            b = data[offset++] & 0xFF;
            size |= ((long)(b & 0x7F)) << shift;
            shift += 7;
        }

        ObjectType type = decodeObjectType(typeCode);
        return new TypeSizeResult(type, (int) size, offset);
    }

    /**
     * Maps a pack type code to an {@link ObjectType}.
     *
     * @param typeCode the pack type code (1=COMMIT, 2=TREE, 3=BLOB)
     * @return the corresponding {@link ObjectType}
     * @throws PackIntegrityException if the type code is unrecognised
     */
    private static ObjectType decodeObjectType(int typeCode) {
        return switch (typeCode) {
            case PackFileEncoder.TYPE_COMMIT -> ObjectType.COMMIT;
            case PackFileEncoder.TYPE_TREE   -> ObjectType.TREE;
            case PackFileEncoder.TYPE_BLOB   -> ObjectType.BLOB;
            default -> throw new PackIntegrityException(
                    "Unknown pack object type code: " + typeCode);
        };
    }

    /**
     * Inflates zlib-compressed bytes, consuming exactly as many compressed bytes as needed
     * to produce {@code expectedSize} uncompressed bytes.
     *
     * <p>Uses {@link java.util.zip.Inflater} directly (rather than {@link InflaterInputStream})
     * so that {@link java.util.zip.Inflater#getBytesRead()} gives the precise number of
     * compressed bytes consumed — important when multiple objects are packed back-to-back.
     *
     * @param compressedData  the compressed bytes (may contain trailing data for subsequent objects)
     * @param expectedSize    the expected uncompressed size
     * @return an {@link InflaterResult} with the inflated data and the number of compressed bytes consumed
     * @throws IOException if inflation fails
     */
    private static InflaterResult inflate(byte[] compressedData, int expectedSize) throws IOException {
        java.util.zip.Inflater inflater = new java.util.zip.Inflater();
        try {
            inflater.setInput(compressedData);
            byte[] inflated = new byte[expectedSize];
            int totalInflated = 0;
            while (totalInflated < expectedSize && !inflater.finished()) {
                try {
                    int n = inflater.inflate(inflated, totalInflated, expectedSize - totalInflated);
                    if (n == 0 && inflater.needsInput()) {
                        break; // no more compressed data available
                    }
                    totalInflated += n;
                } catch (java.util.zip.DataFormatException e) {
                    throw new PackIntegrityException("Zlib inflation failed: " + e.getMessage(), e);
                }
            }
            if (totalInflated != expectedSize) {
                throw new PackIntegrityException(
                        "Inflated size mismatch: expected " + expectedSize + " but got " + totalInflated);
            }
            // getBytesRead() returns the exact number of compressed input bytes consumed
            int consumed = (int) inflater.getBytesRead();
            return new InflaterResult(inflated, consumed);
        } finally {
            inflater.end();
        }
    }

    /**
     * Computes the SHA-256 digest of the given bytes.
     *
     * @param data the bytes to hash
     * @return 32-byte SHA-256 digest
     */
    private static byte[] sha256(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(data);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    // -------------------------------------------------------------------------
    // Internal result types
    // -------------------------------------------------------------------------

    /** Holds the decoded type, uncompressed size, and the offset after the header. */
    private record TypeSizeResult(ObjectType type, int uncompressedSize, int nextOffset) {}

    /** Holds the inflated data and the number of compressed bytes consumed. */
    private record InflaterResult(byte[] data, int compressedBytesConsumed) {}
}
