package com.dvcs.git.pack;

import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

/**
 * Produces and applies simple copy/insert delta instruction streams for binary data.
 *
 * <h2>Delta format</h2>
 * <pre>
 *   Header:
 *     4 bytes — base length (big-endian)
 *     4 bytes — target length (big-endian)
 *
 *   Instructions (repeated until target is fully reconstructed):
 *
 *     COPY instruction:
 *       1 byte  — opcode: 0x80 | flags
 *       flags bits 0–3: which of the 4 offset bytes are present (LSB first)
 *       flags bits 4–6: which of the 3 size bytes are present (LSB first)
 *       0–4 bytes — offset bytes (only those flagged)
 *       0–3 bytes — size bytes (only those flagged)
 *       Copies [offset, offset+size) from base to output.
 *
 *     INSERT instruction:
 *       1 byte  — opcode: 0x00–0x7F (the count of literal bytes that follow)
 *       N bytes — literal bytes to append to output
 *       Maximum insert size per instruction: 127 bytes.
 * </pre>
 *
 * <p>The compression strategy uses a sliding-window hash to find matching regions
 * in the base. Matches shorter than {@value #MIN_COPY_SIZE} bytes are emitted as
 * INSERT instructions instead.
 *
 * <p>Requirement 4 / Req 6: Pack-File Transfer Format — delta compression.
 */
@Component
public class DeltaCompressor {

    /** Minimum match length to emit a COPY instruction (shorter matches are cheaper as INSERT). */
    private static final int MIN_COPY_SIZE = 4;

    /** Maximum number of literal bytes in a single INSERT instruction. */
    private static final int MAX_INSERT_SIZE = 127;

    /** Hash table size for the rolling-hash index (power of 2 for fast modulo). */
    private static final int HASH_TABLE_SIZE = 1 << 16; // 65536

    /** Mask for the hash table. */
    private static final int HASH_MASK = HASH_TABLE_SIZE - 1;

    /**
     * Compresses {@code target} relative to {@code base} into a delta instruction stream.
     *
     * <p>The algorithm builds a hash index over all {@link #MIN_COPY_SIZE}-byte windows
     * in {@code base}, then scans {@code target} looking for matching windows. When a
     * match is found it is extended as far as possible and emitted as a COPY instruction.
     * Unmatched bytes are buffered and emitted as INSERT instructions.
     *
     * @param base   the reference (source) bytes; must not be {@code null}
     * @param target the bytes to encode relative to {@code base}; must not be {@code null}
     * @return the delta instruction stream
     * @throws NullPointerException if either argument is {@code null}
     */
    public byte[] compress(byte[] base, byte[] target) {
        Objects.requireNonNull(base,   "base must not be null");
        Objects.requireNonNull(target, "target must not be null");

        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();

            // Write header: base length + target length
            out.write(toBeInt(base.length));
            out.write(toBeInt(target.length));

            // Build hash index over base: hash(base[i..i+MIN_COPY_SIZE]) → i
            // We use a simple chained hash table (array of linked lists via next[]).
            int[] hashHead = new int[HASH_TABLE_SIZE];
            int[] next     = new int[base.length];
            Arrays.fill(hashHead, -1);
            Arrays.fill(next,     -1);

            for (int i = 0; i <= base.length - MIN_COPY_SIZE; i++) {
                int h = hash(base, i) & HASH_MASK;
                next[i]     = hashHead[h];
                hashHead[h] = i;
            }

            // Scan target, emitting COPY or INSERT instructions
            ByteArrayOutputStream insertBuf = new ByteArrayOutputStream();
            int tPos = 0;

            while (tPos < target.length) {
                int bestOffset = -1;
                int bestLength = 0;

                // Try to find a match in base if enough bytes remain
                if (tPos + MIN_COPY_SIZE <= target.length) {
                    int h = hash(target, tPos) & HASH_MASK;
                    int bPos = hashHead[h];

                    while (bPos != -1) {
                        // Verify and extend the match
                        int matchLen = 0;
                        int maxLen = Math.min(base.length - bPos, target.length - tPos);
                        while (matchLen < maxLen && base[bPos + matchLen] == target[tPos + matchLen]) {
                            matchLen++;
                        }
                        if (matchLen >= MIN_COPY_SIZE && matchLen > bestLength) {
                            bestOffset = bPos;
                            bestLength = matchLen;
                        }
                        bPos = next[bPos];
                    }
                }

                if (bestLength >= MIN_COPY_SIZE) {
                    // Flush any buffered INSERT bytes first
                    flushInsert(insertBuf, out);

                    // Emit COPY instruction
                    writeCopy(out, bestOffset, bestLength);
                    tPos += bestLength;
                } else {
                    // Buffer this byte for INSERT
                    insertBuf.write(target[tPos++]);

                    // Flush if buffer is full
                    if (insertBuf.size() >= MAX_INSERT_SIZE) {
                        flushInsert(insertBuf, out);
                    }
                }
            }

            // Flush any remaining INSERT bytes
            flushInsert(insertBuf, out);

            return out.toByteArray();
        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new IllegalStateException("Unexpected I/O error during delta compression", e);
        }
    }

    /**
     * Reconstructs the target bytes by applying a delta instruction stream to {@code base}.
     *
     * @param base  the reference (source) bytes; must not be {@code null}
     * @param delta the delta instruction stream produced by {@link #compress}; must not be {@code null}
     * @return the reconstructed target bytes
     * @throws IllegalArgumentException if the delta is malformed or inconsistent with {@code base}
     * @throws NullPointerException     if either argument is {@code null}
     */
    public byte[] apply(byte[] base, byte[] delta) {
        Objects.requireNonNull(base,  "base must not be null");
        Objects.requireNonNull(delta, "delta must not be null");

        if (delta.length < 8) {
            throw new IllegalArgumentException(
                    "Delta too short: " + delta.length + " bytes (minimum 8 for header)");
        }

        // Read header
        int baseLen   = readBeInt(delta, 0);
        int targetLen = readBeInt(delta, 4);

        if (baseLen != base.length) {
            throw new IllegalArgumentException(
                    "Delta base length mismatch: delta expects " + baseLen +
                    " but base has " + base.length + " bytes");
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream(targetLen);
        int pos = 8; // skip header

        while (pos < delta.length) {
            int opcode = delta[pos++] & 0xFF;

            if (opcode == 0) {
                throw new IllegalArgumentException(
                        "Invalid delta opcode 0x00 at position " + (pos - 1));
            }

            if ((opcode & 0x80) != 0) {
                // COPY instruction
                int offset = 0;
                int size   = 0;

                // Read offset bytes (flags bits 0–3)
                if ((opcode & 0x01) != 0) offset  |= (delta[pos++] & 0xFF);
                if ((opcode & 0x02) != 0) offset  |= (delta[pos++] & 0xFF) << 8;
                if ((opcode & 0x04) != 0) offset  |= (delta[pos++] & 0xFF) << 16;
                if ((opcode & 0x08) != 0) offset  |= (delta[pos++] & 0xFF) << 24;

                // Read size bytes (flags bits 4–6)
                if ((opcode & 0x10) != 0) size    |= (delta[pos++] & 0xFF);
                if ((opcode & 0x20) != 0) size    |= (delta[pos++] & 0xFF) << 8;
                if ((opcode & 0x40) != 0) size    |= (delta[pos++] & 0xFF) << 16;

                // A size of 0 in the delta format means 0x10000 (65536)
                if (size == 0) size = 0x10000;

                if (offset + size > base.length) {
                    throw new IllegalArgumentException(
                            "COPY instruction out of bounds: offset=" + offset + " size=" + size +
                            " base.length=" + base.length);
                }
                out.write(base, offset, size);

            } else {
                // INSERT instruction: opcode is the byte count (1–127)
                int count = opcode & 0x7F;
                if (pos + count > delta.length) {
                    throw new IllegalArgumentException(
                            "INSERT instruction overruns delta at position " + pos);
                }
                out.write(delta, pos, count);
                pos += count;
            }
        }

        byte[] result = out.toByteArray();
        if (result.length != targetLen) {
            throw new IllegalArgumentException(
                    "Reconstructed target length " + result.length +
                    " does not match expected " + targetLen);
        }
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Writes a COPY instruction to {@code out}.
     *
     * <p>The opcode byte is {@code 0x80 | flags} where flags encode which offset
     * and size bytes are non-zero and therefore present.
     *
     * @param out    the output stream
     * @param offset the copy offset into base
     * @param size   the number of bytes to copy
     * @throws IOException if writing fails
     */
    private static void writeCopy(ByteArrayOutputStream out, int offset, int size) throws IOException {
        int flags = 0;
        ByteArrayOutputStream extra = new ByteArrayOutputStream(7);

        // Encode offset (up to 4 bytes, LSB first)
        if ((offset & 0xFF) != 0)       { flags |= 0x01; extra.write(offset & 0xFF); }
        if ((offset & 0xFF00) != 0)     { flags |= 0x02; extra.write((offset >> 8)  & 0xFF); }
        if ((offset & 0xFF0000) != 0)   { flags |= 0x04; extra.write((offset >> 16) & 0xFF); }
        if ((offset & 0xFF000000) != 0) { flags |= 0x08; extra.write((offset >> 24) & 0xFF); }

        // Encode size (up to 3 bytes, LSB first); size 0 means 0x10000
        int encSize = (size == 0x10000) ? 0 : size;
        if ((encSize & 0xFF) != 0)     { flags |= 0x10; extra.write(encSize & 0xFF); }
        if ((encSize & 0xFF00) != 0)   { flags |= 0x20; extra.write((encSize >> 8)  & 0xFF); }
        if ((encSize & 0xFF0000) != 0) { flags |= 0x40; extra.write((encSize >> 16) & 0xFF); }

        // If all flags are zero (offset=0, size=0x10000), we still need to write something
        // In practice size >= MIN_COPY_SIZE so size is never 0x10000 here, but handle it:
        if (flags == 0) {
            // offset=0, size=0x10000: write opcode with no extra bytes
            out.write(0x80);
            return;
        }

        out.write(0x80 | flags);
        out.write(extra.toByteArray());
    }

    /**
     * Flushes the INSERT buffer to {@code out} as one or more INSERT instructions,
     * then resets the buffer.
     *
     * @param insertBuf the buffer of literal bytes to insert
     * @param out       the output stream
     * @throws IOException if writing fails
     */
    private static void flushInsert(ByteArrayOutputStream insertBuf, ByteArrayOutputStream out)
            throws IOException {
        if (insertBuf.size() == 0) return;

        byte[] bytes = insertBuf.toByteArray();
        int written = 0;
        while (written < bytes.length) {
            int chunk = Math.min(MAX_INSERT_SIZE, bytes.length - written);
            out.write(chunk); // opcode = byte count (1–127)
            out.write(bytes, written, chunk);
            written += chunk;
        }
        insertBuf.reset();
    }

    /**
     * Computes a simple rolling hash over {@link #MIN_COPY_SIZE} bytes starting at
     * {@code offset} in {@code data}.
     *
     * @param data   the byte array
     * @param offset the starting offset
     * @return the hash value
     */
    private static int hash(byte[] data, int offset) {
        int h = 0;
        for (int i = 0; i < MIN_COPY_SIZE; i++) {
            h = h * 31 + (data[offset + i] & 0xFF);
        }
        return h;
    }

    /**
     * Converts a 32-bit integer to a 4-byte big-endian array.
     *
     * @param value the integer to convert
     * @return 4-byte big-endian representation
     */
    private static byte[] toBeInt(int value) {
        return new byte[]{
            (byte) (value >>> 24),
            (byte) (value >>> 16),
            (byte) (value >>> 8),
            (byte)  value
        };
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
}
