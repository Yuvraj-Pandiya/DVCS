package com.dvcs.git.pack;

import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.ObjectType;
import com.dvcs.git.object.SHA256Util;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.storage.ObjectStoreService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PackFileEncoder}, {@link PackFileDecoder}, and {@link DeltaCompressor}.
 *
 * <p>Correctness properties verified:
 * <ul>
 *   <li>Encoding a single blob and decoding the pack returns the same raw bytes with the correct SHA.</li>
 *   <li>Encoding 5 mixed objects (blobs, tree, commit) and decoding returns all 5 with correct SHAs.</li>
 *   <li>A tampered trailer byte causes {@link PackIntegrityException}.</li>
 *   <li>A tampered magic byte causes {@link PackIntegrityException}.</li>
 *   <li>Delta compress + apply round-trips correctly for various inputs.</li>
 *   <li>Delta apply with mismatched base length throws {@link IllegalArgumentException}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class PackFileCodecTest {

    private static final String REPO_ID = "test-repo-1";

    @Mock
    private ObjectStoreService objectStoreService;

    private PackFileEncoder encoder;
    private PackFileDecoder decoder;
    private DeltaCompressor deltaCompressor;

    @BeforeEach
    void setUp() {
        encoder        = new PackFileEncoder(objectStoreService);
        decoder        = new PackFileDecoder();
        deltaCompressor = new DeltaCompressor();
    }

    // =========================================================================
    // PackFileEncoder + PackFileDecoder round-trip tests
    // =========================================================================

    @Nested
    @DisplayName("encode → decode round-trip")
    class RoundTrip {

        @Test
        @DisplayName("single blob: decoded object has same bytes and correct SHA")
        void singleBlob_decodesCorrectly() throws IOException {
            // Arrange
            byte[] content = "Hello, pack-file world!".getBytes(StandardCharsets.UTF_8);
            BlobObject blob = new BlobObject(content);
            byte[] rawBytes = blob.serialize();
            String sha = SHA256Util.computeHex(rawBytes);

            when(objectStoreService.readObject(REPO_ID, sha)).thenReturn(rawBytes);

            // Act
            byte[] pack = encoder.encode(REPO_ID, List.of(sha));
            List<RawObject> decoded = decoder.decode(new ByteArrayInputStream(pack));

            // Assert
            assertThat(decoded).hasSize(1);
            RawObject obj = decoded.get(0);
            assertThat(obj.type()).isEqualTo(ObjectType.BLOB);
            assertThat(obj.sha()).isEqualTo(sha);
            assertThat(obj.data()).isEqualTo(rawBytes);
        }

        @Test
        @DisplayName("five mixed objects: all decoded with correct types and SHAs")
        void fiveMixedObjects_allDecodedCorrectly() throws IOException {
            // Arrange — create 3 blobs, 1 tree, 1 commit
            BlobObject blob1 = new BlobObject("file one content".getBytes(StandardCharsets.UTF_8));
            BlobObject blob2 = new BlobObject("file two content".getBytes(StandardCharsets.UTF_8));
            BlobObject blob3 = new BlobObject("file three content".getBytes(StandardCharsets.UTF_8));

            TreeObject tree = new TreeObject(List.of(
                    new TreeEntry("100644", "file1.txt", blob1.getSha()),
                    new TreeEntry("100644", "file2.txt", blob2.getSha()),
                    new TreeEntry("100644", "file3.txt", blob3.getSha())
            ));

            CommitObject commit = new CommitObject(
                    tree.getSha(),
                    List.of(),
                    "Alice", "alice@example.com", 1700000000L,
                    "Alice", "alice@example.com", 1700000000L,
                    "Initial commit"
            );

            // Build SHA → rawBytes map and stub the mock
            record Entry(String sha, byte[] raw, ObjectType type) {}
            List<Entry> entries = List.of(
                    new Entry(SHA256Util.computeHex(blob1.serialize()),   blob1.serialize(),   ObjectType.BLOB),
                    new Entry(SHA256Util.computeHex(blob2.serialize()),   blob2.serialize(),   ObjectType.BLOB),
                    new Entry(SHA256Util.computeHex(blob3.serialize()),   blob3.serialize(),   ObjectType.BLOB),
                    new Entry(SHA256Util.computeHex(tree.serialize()),    tree.serialize(),    ObjectType.TREE),
                    new Entry(SHA256Util.computeHex(commit.serialize()),  commit.serialize(),  ObjectType.COMMIT)
            );

            List<String> shas = entries.stream().map(Entry::sha).toList();
            for (Entry e : entries) {
                when(objectStoreService.readObject(REPO_ID, e.sha())).thenReturn(e.raw());
            }

            // Act
            byte[] pack = encoder.encode(REPO_ID, shas);
            List<RawObject> decoded = decoder.decode(new ByteArrayInputStream(pack));

            // Assert — all 5 objects present with correct types and SHAs
            assertThat(decoded).hasSize(5);
            for (int i = 0; i < 5; i++) {
                Entry expected = entries.get(i);
                RawObject actual = decoded.get(i);
                assertThat(actual.type()).as("type at index %d", i).isEqualTo(expected.type());
                assertThat(actual.sha()).as("sha at index %d", i).isEqualTo(expected.sha());
                assertThat(actual.data()).as("data at index %d", i).isEqualTo(expected.raw());
            }
        }

        @Test
        @DisplayName("empty object list: pack encodes and decodes to empty list")
        void emptyObjectList_decodesCorrectly() throws IOException {
            // Act
            byte[] pack = encoder.encode(REPO_ID, List.of());
            List<RawObject> decoded = decoder.decode(new ByteArrayInputStream(pack));

            // Assert
            assertThat(decoded).isEmpty();
        }

        @Test
        @DisplayName("large blob (100 KB): round-trip preserves all bytes")
        void largeBlobRoundTrip() throws IOException {
            // Arrange
            byte[] content = new byte[100_000];
            for (int i = 0; i < content.length; i++) {
                content[i] = (byte) (i % 256);
            }
            BlobObject blob = new BlobObject(content);
            byte[] rawBytes = blob.serialize();
            String sha = SHA256Util.computeHex(rawBytes);

            when(objectStoreService.readObject(REPO_ID, sha)).thenReturn(rawBytes);

            // Act
            byte[] pack = encoder.encode(REPO_ID, List.of(sha));
            List<RawObject> decoded = decoder.decode(new ByteArrayInputStream(pack));

            // Assert
            assertThat(decoded).hasSize(1);
            assertThat(decoded.get(0).data()).isEqualTo(rawBytes);
        }
    }

    // =========================================================================
    // Integrity / tamper tests
    // =========================================================================

    @Nested
    @DisplayName("pack integrity")
    class Integrity {

        @Test
        @DisplayName("tampered trailer byte causes PackIntegrityException")
        void tamperedTrailer_throwsPackIntegrityException() throws IOException {
            // Arrange
            byte[] content = "tamper test".getBytes(StandardCharsets.UTF_8);
            BlobObject blob = new BlobObject(content);
            byte[] rawBytes = blob.serialize();
            String sha = SHA256Util.computeHex(rawBytes);

            when(objectStoreService.readObject(REPO_ID, sha)).thenReturn(rawBytes);

            byte[] pack = encoder.encode(REPO_ID, List.of(sha));

            // Tamper the last byte of the trailer
            pack[pack.length - 1] ^= 0xFF;

            // Act & Assert
            assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(pack)))
                    .isInstanceOf(PackIntegrityException.class)
                    .hasMessageContaining("trailer");
        }

        @Test
        @DisplayName("tampered first trailer byte causes PackIntegrityException")
        void tamperedFirstTrailerByte_throwsPackIntegrityException() throws IOException {
            // Arrange
            byte[] content = "another tamper test".getBytes(StandardCharsets.UTF_8);
            BlobObject blob = new BlobObject(content);
            byte[] rawBytes = blob.serialize();
            String sha = SHA256Util.computeHex(rawBytes);

            when(objectStoreService.readObject(REPO_ID, sha)).thenReturn(rawBytes);

            byte[] pack = encoder.encode(REPO_ID, List.of(sha));

            // Tamper the first byte of the 32-byte trailer
            pack[pack.length - 32] ^= 0x01;

            // Act & Assert
            assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(pack)))
                    .isInstanceOf(PackIntegrityException.class);
        }

        @Test
        @DisplayName("tampered magic bytes causes PackIntegrityException")
        void tamperedMagic_throwsPackIntegrityException() throws IOException {
            // Arrange
            byte[] content = "magic tamper".getBytes(StandardCharsets.UTF_8);
            BlobObject blob = new BlobObject(content);
            byte[] rawBytes = blob.serialize();
            String sha = SHA256Util.computeHex(rawBytes);

            when(objectStoreService.readObject(REPO_ID, sha)).thenReturn(rawBytes);

            byte[] pack = encoder.encode(REPO_ID, List.of(sha));

            // Corrupt the magic bytes
            pack[0] = 'X';

            // Act & Assert — trailer check fires first (magic corruption changes body hash)
            assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(pack)))
                    .isInstanceOf(PackIntegrityException.class);
        }

        @Test
        @DisplayName("pack too short throws PackIntegrityException")
        void packTooShort_throwsPackIntegrityException() {
            byte[] tooShort = new byte[10];

            assertThatThrownBy(() -> decoder.decode(new ByteArrayInputStream(tooShort)))
                    .isInstanceOf(PackIntegrityException.class)
                    .hasMessageContaining("too short");
        }

        @Test
        @DisplayName("null stream throws NullPointerException")
        void nullStream_throwsNullPointerException() {
            assertThatThrownBy(() -> decoder.decode(null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // DeltaCompressor tests
    // =========================================================================

    @Nested
    @DisplayName("DeltaCompressor")
    class DeltaCompressorTests {

        @Test
        @DisplayName("compress + apply round-trips identical content")
        void roundTrip_identicalContent() {
            byte[] base   = "Hello, world! This is a test string.".getBytes(StandardCharsets.UTF_8);
            byte[] target = "Hello, world! This is a test string.".getBytes(StandardCharsets.UTF_8);

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("compress + apply round-trips modified content")
        void roundTrip_modifiedContent() {
            byte[] base   = "The quick brown fox jumps over the lazy dog.".getBytes(StandardCharsets.UTF_8);
            byte[] target = "The quick brown cat jumps over the lazy dog.".getBytes(StandardCharsets.UTF_8);

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("compress + apply round-trips completely different content")
        void roundTrip_differentContent() {
            byte[] base   = "AAAAAAAAAA".getBytes(StandardCharsets.UTF_8);
            byte[] target = "BBBBBBBBBB".getBytes(StandardCharsets.UTF_8);

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("compress + apply round-trips empty target")
        void roundTrip_emptyTarget() {
            byte[] base   = "some base content".getBytes(StandardCharsets.UTF_8);
            byte[] target = new byte[0];

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("compress + apply round-trips empty base")
        void roundTrip_emptyBase() {
            byte[] base   = new byte[0];
            byte[] target = "new content from nothing".getBytes(StandardCharsets.UTF_8);

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("compress + apply round-trips large content with repeated patterns")
        void roundTrip_largeRepeatedContent() {
            // Build a large base with repeated patterns (good for COPY instructions)
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 200; i++) {
                sb.append("line ").append(i).append(": The quick brown fox jumps over the lazy dog.\n");
            }
            byte[] base = sb.toString().getBytes(StandardCharsets.UTF_8);

            // Target: same content with a few lines changed
            String modified = sb.toString().replace("line 50:", "line 50 MODIFIED:");
            byte[] target = modified.getBytes(StandardCharsets.UTF_8);

            byte[] delta  = deltaCompressor.compress(base, target);
            byte[] result = deltaCompressor.apply(base, delta);

            assertThat(result).isEqualTo(target);
        }

        @Test
        @DisplayName("apply with wrong base length throws IllegalArgumentException")
        void apply_wrongBaseLength_throwsIllegalArgumentException() {
            byte[] base   = "correct base".getBytes(StandardCharsets.UTF_8);
            byte[] target = "target content".getBytes(StandardCharsets.UTF_8);
            byte[] delta  = deltaCompressor.compress(base, target);

            byte[] wrongBase = "wrong".getBytes(StandardCharsets.UTF_8);

            assertThatThrownBy(() -> deltaCompressor.apply(wrongBase, delta))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("base length mismatch");
        }

        @Test
        @DisplayName("compress null base throws NullPointerException")
        void compress_nullBase_throwsNullPointerException() {
            assertThatThrownBy(() -> deltaCompressor.compress(null, new byte[0]))
                    .isInstanceOf(NullPointerException.class);
        }

        @Test
        @DisplayName("apply null delta throws NullPointerException")
        void apply_nullDelta_throwsNullPointerException() {
            assertThatThrownBy(() -> deltaCompressor.apply(new byte[0], null))
                    .isInstanceOf(NullPointerException.class);
        }
    }

    // =========================================================================
    // PackFileEncoder helper tests
    // =========================================================================

    @Nested
    @DisplayName("PackFileEncoder helpers")
    class EncoderHelpers {

        @Test
        @DisplayName("encodeTypeSize: small size fits in one byte")
        void encodeTypeSize_smallSize_oneByte() {
            // type=BLOB(3), size=5 → first byte: [0][011][0101] = 0x35, no more bytes
            byte[] encoded = PackFileEncoder.encodeTypeSize(PackFileEncoder.TYPE_BLOB, 5);
            assertThat(encoded).hasSize(1);
            assertThat(encoded[0] & 0x80).isEqualTo(0); // no more-bytes flag
            assertThat((encoded[0] >> 4) & 0x07).isEqualTo(PackFileEncoder.TYPE_BLOB);
            assertThat(encoded[0] & 0x0F).isEqualTo(5);
        }

        @Test
        @DisplayName("encodeTypeSize: size > 15 requires continuation byte")
        void encodeTypeSize_largeSize_continuationByte() {
            // type=COMMIT(1), size=32 → low 4 bits = 0, remainder = 2
            byte[] encoded = PackFileEncoder.encodeTypeSize(PackFileEncoder.TYPE_COMMIT, 32);
            assertThat(encoded.length).isGreaterThan(1);
            assertThat(encoded[0] & 0x80).isNotEqualTo(0); // more-bytes flag set
        }

        @Test
        @DisplayName("toBeInt: converts integer to 4-byte big-endian")
        void toBeInt_correctEncoding() {
            byte[] result = PackFileEncoder.toBeInt(0x01020304);
            assertThat(result).isEqualTo(new byte[]{0x01, 0x02, 0x03, 0x04});
        }

        @Test
        @DisplayName("toBeInt: zero encodes to four zero bytes")
        void toBeInt_zero() {
            assertThat(PackFileEncoder.toBeInt(0)).isEqualTo(new byte[]{0, 0, 0, 0});
        }
    }
}
