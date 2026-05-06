package com.dvcs.git.object;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link CommitObject}.
 *
 * <p>Verifies canonical serialization format, SHA-256 computation, field accessors,
 * and constructor validation.
 */
class CommitObjectTest {

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    /** A valid 64-char lowercase hex SHA-256 string used as a placeholder. */
    private static final String FAKE_SHA =
            "a".repeat(64);

    /** A second valid SHA for parent references. */
    private static final String FAKE_PARENT_SHA =
            "b".repeat(64);

    private CommitObject buildSimpleCommit() {
        return new CommitObject(
                FAKE_SHA,
                Collections.emptyList(),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Alice",
                "alice@example.com",
                1700000000L,
                "Initial commit"
        );
    }

    // -------------------------------------------------------------------------
    // Type and accessors
    // -------------------------------------------------------------------------

    @Test
    void type_isCommit() {
        CommitObject commit = buildSimpleCommit();
        assertEquals(ObjectType.COMMIT, commit.getType());
    }

    @Test
    void accessors_returnConstructorValues() {
        CommitObject commit = new CommitObject(
                FAKE_SHA,
                List.of(FAKE_PARENT_SHA),
                "Bob",
                "bob@example.com",
                1700001000L,
                "Carol",
                "carol@example.com",
                1700002000L,
                "Fix bug"
        );

        assertEquals(FAKE_SHA,          commit.getTreeSha());
        assertEquals(List.of(FAKE_PARENT_SHA), commit.getParentShas());
        assertEquals("Bob",             commit.getAuthorName());
        assertEquals("bob@example.com", commit.getAuthorEmail());
        assertEquals(1700001000L,       commit.getAuthorTimestamp());
        assertEquals("Carol",           commit.getCommitterName());
        assertEquals("carol@example.com", commit.getCommitterEmail());
        assertEquals(1700002000L,       commit.getCommitterTimestamp());
        assertEquals("Fix bug",         commit.getMessage());
    }

    @Test
    void parentShas_isUnmodifiable() {
        CommitObject commit = buildSimpleCommit();
        assertThrows(UnsupportedOperationException.class,
                () -> commit.getParentShas().add("x".repeat(64)));
    }

    // -------------------------------------------------------------------------
    // Serialization format
    // -------------------------------------------------------------------------

    @Test
    void serialize_rootCommit_hasCorrectFormat() {
        CommitObject commit = new CommitObject(
                FAKE_SHA,
                Collections.emptyList(),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Alice",
                "alice@example.com",
                1700000000L,
                "Initial commit"
        );

        String serialized = new String(commit.serialize(), StandardCharsets.UTF_8);

        // Must start with "commit {size}\0"
        assertTrue(serialized.startsWith("commit "), "Must start with 'commit '");
        int nulIndex = serialized.indexOf('\0');
        assertTrue(nulIndex > 0, "Must contain NUL separator");

        String body = serialized.substring(nulIndex + 1);

        // tree line
        assertTrue(body.startsWith("tree " + FAKE_SHA + "\n"),
                "Body must start with tree line");

        // no parent lines for root commit
        assertFalse(body.contains("parent "), "Root commit must have no parent lines");

        // author line
        assertTrue(body.contains("author Alice <alice@example.com> 1700000000 +0000\n"),
                "Must contain correct author line");

        // committer line
        assertTrue(body.contains("committer Alice <alice@example.com> 1700000000 +0000\n"),
                "Must contain correct committer line");

        // blank line before message
        assertTrue(body.contains("\n\nInitial commit"),
                "Must have blank line before message");
    }

    @Test
    void serialize_withOneParent_includesParentLine() {
        CommitObject commit = new CommitObject(
                FAKE_SHA,
                List.of(FAKE_PARENT_SHA),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Alice",
                "alice@example.com",
                1700000000L,
                "Second commit"
        );

        String serialized = new String(commit.serialize(), StandardCharsets.UTF_8);
        int nulIndex = serialized.indexOf('\0');
        String body = serialized.substring(nulIndex + 1);

        assertTrue(body.contains("parent " + FAKE_PARENT_SHA + "\n"),
                "Must contain parent line");
    }

    @Test
    void serialize_withMultipleParents_includesAllParentLines() {
        String parent1 = "1".repeat(64);
        String parent2 = "2".repeat(64);

        CommitObject commit = new CommitObject(
                FAKE_SHA,
                List.of(parent1, parent2),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Alice",
                "alice@example.com",
                1700000000L,
                "Merge commit"
        );

        String serialized = new String(commit.serialize(), StandardCharsets.UTF_8);
        int nulIndex = serialized.indexOf('\0');
        String body = serialized.substring(nulIndex + 1);

        assertTrue(body.contains("parent " + parent1 + "\n"), "Must contain first parent");
        assertTrue(body.contains("parent " + parent2 + "\n"), "Must contain second parent");
    }

    @Test
    void serialize_headerSizeMatchesBodyLength() {
        CommitObject commit = buildSimpleCommit();
        byte[] serialized = commit.serialize();

        // Find the NUL byte position
        int nulPos = -1;
        for (int i = 0; i < serialized.length; i++) {
            if (serialized[i] == 0) {
                nulPos = i;
                break;
            }
        }
        assertTrue(nulPos > 0, "Must contain NUL byte");

        // Parse the size from the header
        String header = new String(serialized, 0, nulPos, StandardCharsets.UTF_8);
        // header is "commit {size}"
        int spacePos = header.indexOf(' ');
        int declaredSize = Integer.parseInt(header.substring(spacePos + 1));

        int actualBodyLength = serialized.length - nulPos - 1;
        assertEquals(declaredSize, actualBodyLength,
                "Declared size in header must equal actual body byte length");
    }

    @Test
    void serialize_bodyOrder_treeBeforeParentsBeforeAuthorBeforeCommitter() {
        CommitObject commit = new CommitObject(
                FAKE_SHA,
                List.of(FAKE_PARENT_SHA),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Bob",
                "bob@example.com",
                1700000001L,
                "Ordered commit"
        );

        String serialized = new String(commit.serialize(), StandardCharsets.UTF_8);
        int nulIndex = serialized.indexOf('\0');
        String body = serialized.substring(nulIndex + 1);

        int treePos      = body.indexOf("tree ");
        int parentPos    = body.indexOf("parent ");
        int authorPos    = body.indexOf("author ");
        int committerPos = body.indexOf("committer ");

        assertTrue(treePos < parentPos,    "tree must come before parent");
        assertTrue(parentPos < authorPos,  "parent must come before author");
        assertTrue(authorPos < committerPos, "author must come before committer");
    }

    @Test
    void serialize_differentTimestamps_authorAndCommitterDistinct() {
        CommitObject commit = new CommitObject(
                FAKE_SHA,
                Collections.emptyList(),
                "Alice",
                "alice@example.com",
                1700000000L,
                "Bob",
                "bob@example.com",
                1700009999L,
                "Distinct timestamps"
        );

        String serialized = new String(commit.serialize(), StandardCharsets.UTF_8);

        assertTrue(serialized.contains("author Alice <alice@example.com> 1700000000 +0000"),
                "Author line must use author timestamp");
        assertTrue(serialized.contains("committer Bob <bob@example.com> 1700009999 +0000"),
                "Committer line must use committer timestamp");
    }

    // -------------------------------------------------------------------------
    // SHA-256 computation
    // -------------------------------------------------------------------------

    @Test
    void sha_is64LowercaseHexChars() {
        CommitObject commit = buildSimpleCommit();
        String sha = commit.getSha();
        assertNotNull(sha);
        assertEquals(64, sha.length(), "SHA must be 64 characters");
        assertTrue(sha.matches("[0-9a-f]{64}"), "SHA must be lowercase hex");
    }

    @Test
    void sha_isDerivedFromSerializedContent() throws Exception {
        CommitObject commit = buildSimpleCommit();
        byte[] serialized = commit.serialize();

        java.security.MessageDigest digest =
                java.security.MessageDigest.getInstance("SHA-256");
        byte[] hashBytes = digest.digest(serialized);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        String expectedSha = sb.toString();

        assertEquals(expectedSha, commit.getSha(),
                "SHA must equal SHA-256 of serialized bytes");
    }

    @Test
    void sha_differentMessages_produceDifferentShas() {
        CommitObject c1 = new CommitObject(FAKE_SHA, Collections.emptyList(),
                "Alice", "alice@example.com", 1700000000L,
                "Alice", "alice@example.com", 1700000000L, "Message A");
        CommitObject c2 = new CommitObject(FAKE_SHA, Collections.emptyList(),
                "Alice", "alice@example.com", 1700000000L,
                "Alice", "alice@example.com", 1700000000L, "Message B");

        assertNotEquals(c1.getSha(), c2.getSha(),
                "Different messages must produce different SHAs");
    }

    @Test
    void sha_sameContent_producesSameSha() {
        CommitObject c1 = buildSimpleCommit();
        CommitObject c2 = buildSimpleCommit();
        assertEquals(c1.getSha(), c2.getSha(),
                "Identical content must produce identical SHA");
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void constructor_nullTreeSha_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new CommitObject(null, Collections.emptyList(),
                        "Alice", "alice@example.com", 0L,
                        "Alice", "alice@example.com", 0L, "msg"));
    }

    @Test
    void constructor_nullParentShas_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new CommitObject(FAKE_SHA, null,
                        "Alice", "alice@example.com", 0L,
                        "Alice", "alice@example.com", 0L, "msg"));
    }

    @Test
    void constructor_nullMessage_throwsNullPointerException() {
        assertThrows(NullPointerException.class, () ->
                new CommitObject(FAKE_SHA, Collections.emptyList(),
                        "Alice", "alice@example.com", 0L,
                        "Alice", "alice@example.com", 0L, null));
    }

    @Test
    void constructor_validTreeSha_doesNotThrow() {
        // treeSha is stored as-is in the commit body; the object's own SHA
        // (computed from the serialized content) is what GitObject validates.
        assertDoesNotThrow(() ->
                new CommitObject(FAKE_SHA, Collections.emptyList(),
                        "Alice", "alice@example.com", 0L,
                        "Alice", "alice@example.com", 0L, "msg"));
    }
}
