package com.dvcs.git.transport;

import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.pack.PackFileEncoder;
import com.dvcs.git.ref.Branch;
import com.dvcs.git.ref.BranchRepository;
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
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link UploadPackServiceImpl}.
 *
 * <p>Verifies:
 * <ul>
 *   <li>advertiseRefs returns pkt-line encoded refs for a repo with branches</li>
 *   <li>advertiseRefs returns capabilities advertisement for empty repo</li>
 *   <li>advertiseRefs includes capabilities in first ref line</li>
 *   <li>advertiseRefs ends with flush-pkt</li>
 *   <li>uploadPack with no wants returns flush-pkt</li>
 *   <li>uploadPack with wants and no haves returns NAK + pack data</li>
 *   <li>parseCommit correctly parses a serialized CommitObject</li>
 *   <li>parseTree correctly parses a serialized TreeObject</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UploadPackServiceImpl")
class UploadPackServiceImplTest {

    private static final Long REPO_ID = 42L;
    private static final String REPO_ID_STR = "42";

    @Mock
    private BranchRepository branchRepository;

    @Mock
    private ObjectStoreService objectStoreService;

    @Mock
    private PackFileEncoder packFileEncoder;

    private UploadPackServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new UploadPackServiceImpl(branchRepository, objectStoreService, packFileEncoder);
    }

    // -------------------------------------------------------------------------
    // advertiseRefs
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("advertiseRefs")
    class AdvertiseRefs {

        @Test
        @DisplayName("empty repo returns capabilities advertisement ending with flush-pkt")
        void emptyRepo_returnsCapabilitiesAdvertisement() {
            when(branchRepository.findByRepoId(REPO_ID)).thenReturn(List.of());

            byte[] result = service.advertiseRefs(REPO_ID);
            String text = new String(result, StandardCharsets.UTF_8);

            // Should contain the zero SHA and capabilities^{}
            assertThat(text).contains("capabilities^{}");
            assertThat(text).contains("side-band-64k");
            // Should end with flush-pkt
            assertThat(text).endsWith("0000");
        }

        @Test
        @DisplayName("repo with one branch returns ref line with capabilities and flush-pkt")
        void oneBranch_returnsRefLineWithCapabilities() {
            String sha = "a".repeat(64);
            Branch branch = new Branch(REPO_ID, "main", sha, false, OffsetDateTime.now());
            when(branchRepository.findByRepoId(REPO_ID)).thenReturn(List.of(branch));

            byte[] result = service.advertiseRefs(REPO_ID);
            String text = new String(result, StandardCharsets.UTF_8);

            // First ref line should contain SHA, ref name, and capabilities after NUL
            assertThat(text).contains(sha);
            assertThat(text).contains("refs/heads/main");
            assertThat(text).contains("side-band-64k");
            assertThat(text).contains("ofs-delta");
            assertThat(text).contains("shallow");
            assertThat(text).contains("no-progress");
            assertThat(text).contains("include-tag");
            // Should end with flush-pkt
            assertThat(text).endsWith("0000");
        }

        @Test
        @DisplayName("repo with multiple branches: only first ref has capabilities")
        void multipleBranches_onlyFirstHasCapabilities() {
            String sha1 = "a".repeat(64);
            String sha2 = "b".repeat(64);
            Branch main = new Branch(REPO_ID, "main", sha1, false, OffsetDateTime.now());
            Branch dev = new Branch(REPO_ID, "dev", sha2, false, OffsetDateTime.now());
            when(branchRepository.findByRepoId(REPO_ID)).thenReturn(List.of(main, dev));

            byte[] result = service.advertiseRefs(REPO_ID);
            String text = new String(result, StandardCharsets.UTF_8);

            assertThat(text).contains(sha1);
            assertThat(text).contains(sha2);
            assertThat(text).contains("refs/heads/main");
            assertThat(text).contains("refs/heads/dev");
            // Capabilities appear only once (after first ref's NUL)
            assertThat(text.indexOf("side-band-64k")).isEqualTo(text.lastIndexOf("side-band-64k"));
        }

        @Test
        @DisplayName("each ref is encoded as a valid pkt-line")
        void eachRef_isValidPktLine() throws IOException {
            String sha = "c".repeat(64);
            Branch branch = new Branch(REPO_ID, "feature", sha, false, OffsetDateTime.now());
            when(branchRepository.findByRepoId(REPO_ID)).thenReturn(List.of(branch));

            byte[] result = service.advertiseRefs(REPO_ID);
            ByteArrayInputStream in = new ByteArrayInputStream(result);

            // First line should be readable as a pkt-line
            String line = PktLineUtil.readLine(in);
            assertThat(line).isNotNull();
            assertThat(line).contains(sha);
            assertThat(line).contains("refs/heads/feature");

            // Next should be flush-pkt
            String flush = PktLineUtil.readLine(in);
            assertThat(flush).isNull();
        }
    }

    // -------------------------------------------------------------------------
    // uploadPack
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("uploadPack")
    class UploadPack {

        @Test
        @DisplayName("empty input (no wants) returns flush-pkt")
        void emptyInput_returnsFlushPkt() throws IOException {
            // Empty stream — no want lines
            InputStream emptyInput = new ByteArrayInputStream(PktLineUtil.FLUSH_PKT);

            InputStream result = service.uploadPack(REPO_ID, emptyInput);
            byte[] bytes = result.readAllBytes();

            assertThat(bytes).isEqualTo(PktLineUtil.FLUSH_PKT);
        }

        @Test
        @DisplayName("wants with no haves returns NAK followed by pack data")
        void wantsNoHaves_returnsNakAndPack() throws Exception {
            // Build a real commit to use as the wanted SHA
            BlobObject blob = new BlobObject("hello".getBytes(StandardCharsets.UTF_8));
            TreeObject tree = new TreeObject(
                    List.of(new TreeEntry("100644", "hello.txt", blob.getSha())));
            CommitObject commit = new CommitObject(
                    tree.getSha(), List.of(),
                    "Author", "author@example.com", 1000L,
                    "Committer", "committer@example.com", 1000L,
                    "Initial commit");

            String commitSha = commit.getSha();
            String treeSha = tree.getSha();
            String blobSha = blob.getSha();

            // Mock object store to return serialized objects
            when(objectStoreService.readObject(REPO_ID_STR, commitSha))
                    .thenReturn(commit.serialize());
            when(objectStoreService.readObject(REPO_ID_STR, treeSha))
                    .thenReturn(tree.serialize());
            // blob SHA is collected but not read during graph walk

            byte[] fakePack = "PACK-DATA".getBytes(StandardCharsets.UTF_8);
            when(packFileEncoder.encode(eq(REPO_ID_STR), anyList())).thenReturn(fakePack);

            // Build want/have input
            byte[] wantLine = PktLineUtil.encodeLine("want " + commitSha + "\n");
            byte[] flush = PktLineUtil.FLUSH_PKT;
            byte[] done = PktLineUtil.encodeLine("done\n");
            byte[] input = concat(wantLine, flush, done);

            InputStream result = service.uploadPack(REPO_ID, new ByteArrayInputStream(input));
            byte[] responseBytes = result.readAllBytes();
            String responseText = new String(responseBytes, StandardCharsets.UTF_8);

            // Should contain NAK
            assertThat(responseText).contains("NAK");
            // Should contain pack data
            assertThat(responseText).contains("PACK-DATA");
        }

        @Test
        @DisplayName("wants with matching haves returns ACK lines")
        void wantsWithMatchingHaves_returnsAck() throws Exception {
            BlobObject blob = new BlobObject("content".getBytes(StandardCharsets.UTF_8));
            TreeObject tree = new TreeObject(
                    List.of(new TreeEntry("100644", "file.txt", blob.getSha())));
            CommitObject commit = new CommitObject(
                    tree.getSha(), List.of(),
                    "Author", "a@b.com", 2000L,
                    "Committer", "c@d.com", 2000L,
                    "Commit");

            String commitSha = commit.getSha();
            String haveSha = blob.getSha();

            when(objectStoreService.readObject(REPO_ID_STR, commitSha))
                    .thenReturn(commit.serialize());
            when(objectStoreService.readObject(REPO_ID_STR, tree.getSha()))
                    .thenReturn(tree.serialize());
            // blob is a "have" — but we still need to check it exists
            when(objectStoreService.readObject(REPO_ID_STR, haveSha))
                    .thenReturn(blob.serialize());

            byte[] fakePack = "PACK".getBytes(StandardCharsets.UTF_8);
            when(packFileEncoder.encode(eq(REPO_ID_STR), anyList())).thenReturn(fakePack);

            byte[] wantLine = PktLineUtil.encodeLine("want " + commitSha + "\n");
            byte[] haveLine = PktLineUtil.encodeLine("have " + haveSha + "\n");
            byte[] flush = PktLineUtil.FLUSH_PKT;
            byte[] done = PktLineUtil.encodeLine("done\n");
            byte[] input = concat(wantLine, haveLine, flush, done);

            InputStream result = service.uploadPack(REPO_ID, new ByteArrayInputStream(input));
            byte[] responseBytes = result.readAllBytes();
            String responseText = new String(responseBytes, StandardCharsets.UTF_8);

            // Should contain ACK for the have SHA
            assertThat(responseText).contains("ACK");
            assertThat(responseText).contains(haveSha);
        }
    }

    // -------------------------------------------------------------------------
    // parseCommit / parseTree (static helpers)
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("parseCommit")
    class ParseCommit {

        @Test
        @DisplayName("round-trip: serialize then parse returns same fields")
        void roundTrip_serializeThenParse() {
            String treeSha = "d".repeat(64);
            String parentSha = "e".repeat(64);
            CommitObject original = new CommitObject(
                    treeSha, List.of(parentSha),
                    "Alice", "alice@example.com", 1234567890L,
                    "Bob", "bob@example.com", 1234567891L,
                    "My commit message");

            byte[] serialized = original.serialize();
            CommitObject parsed = UploadPackServiceImpl.parseCommit(serialized);

            assertThat(parsed.getTreeSha()).isEqualTo(treeSha);
            assertThat(parsed.getParentShas()).containsExactly(parentSha);
            assertThat(parsed.getAuthorName()).isEqualTo("Alice");
            assertThat(parsed.getAuthorEmail()).isEqualTo("alice@example.com");
            assertThat(parsed.getAuthorTimestamp()).isEqualTo(1234567890L);
            assertThat(parsed.getMessage()).isEqualTo("My commit message");
        }

        @Test
        @DisplayName("root commit (no parents) parses correctly")
        void rootCommit_noParents() {
            String treeSha = "f".repeat(64);
            CommitObject original = new CommitObject(
                    treeSha, List.of(),
                    "Dev", "dev@example.com", 0L,
                    "Dev", "dev@example.com", 0L,
                    "Root");

            CommitObject parsed = UploadPackServiceImpl.parseCommit(original.serialize());
            assertThat(parsed.getParentShas()).isEmpty();
            assertThat(parsed.getTreeSha()).isEqualTo(treeSha);
        }
    }

    @Nested
    @DisplayName("parseTree")
    class ParseTree {

        @Test
        @DisplayName("round-trip: serialize then parse returns same entries")
        void roundTrip_serializeThenParse() {
            String blobSha = "1".repeat(64);
            String subTreeSha = "2".repeat(64);
            TreeObject original = new TreeObject(List.of(
                    new TreeEntry("100644", "README.md", blobSha),
                    new TreeEntry("040000", "src", subTreeSha)
            ));

            byte[] serialized = original.serialize();
            TreeObject parsed = UploadPackServiceImpl.parseTree(serialized);

            assertThat(parsed.getEntries()).hasSize(2);
            assertThat(parsed.getEntries().get(0).name()).isEqualTo("README.md");
            assertThat(parsed.getEntries().get(0).sha()).isEqualTo(blobSha);
            assertThat(parsed.getEntries().get(0).mode()).isEqualTo("100644");
            assertThat(parsed.getEntries().get(1).name()).isEqualTo("src");
            assertThat(parsed.getEntries().get(1).sha()).isEqualTo(subTreeSha);
            assertThat(parsed.getEntries().get(1).mode()).isEqualTo("040000");
        }

        @Test
        @DisplayName("empty tree parses to empty entry list")
        void emptyTree_parsesToEmptyList() {
            TreeObject original = new TreeObject(List.of());
            TreeObject parsed = UploadPackServiceImpl.parseTree(original.serialize());
            assertThat(parsed.getEntries()).isEmpty();
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private static byte[] concat(byte[]... arrays) {
        int total = 0;
        for (byte[] a : arrays) total += a.length;
        byte[] result = new byte[total];
        int pos = 0;
        for (byte[] a : arrays) {
            System.arraycopy(a, 0, result, pos, a.length);
            pos += a.length;
        }
        return result;
    }
}
