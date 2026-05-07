package com.dvcs.git.transport;

import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.pack.PackFileEncoder;
import com.dvcs.git.ref.Branch;
import com.dvcs.git.ref.BranchRepository;
import com.dvcs.git.storage.ObjectStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Full implementation of {@link UploadPackService} for the Git smart HTTP
 * upload-pack protocol (clone / fetch).
 *
 * <h2>advertiseRefs</h2>
 * <p>Returns a pkt-line encoded ref advertisement listing all branches in the
 * repository. The first ref line includes a NUL-separated capability string.
 * An empty repository returns a special capabilities advertisement.
 *
 * <h2>uploadPack</h2>
 * <p>Parses the client's want/have negotiation, walks the commit graph to
 * determine which objects to send, builds a pack file via {@link PackFileEncoder},
 * and prepends a NAK or ACK response line.
 *
 * <p>Requirement 6: HTTP Smart Git Transport — upload-pack protocol.
 */
@Service
@Primary
public class UploadPackServiceImpl implements UploadPackService {

    private static final Logger log = LoggerFactory.getLogger(UploadPackServiceImpl.class);

    /** Capabilities advertised to the client (space-separated). */
    private static final String CAPABILITIES =
            "side-band-64k ofs-delta shallow no-progress include-tag";

    /** Maximum number of commits to walk when computing the object set. */
    private static final int MAX_COMMIT_WALK = 1000;

    /** Zero SHA used in the empty-repo capabilities advertisement. */
    private static final String ZERO_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    private final BranchRepository branchRepository;
    private final ObjectStoreService objectStoreService;
    private final PackFileEncoder packFileEncoder;

    /**
     * Constructs an {@code UploadPackServiceImpl}.
     *
     * @param branchRepository   repository for branch lookups
     * @param objectStoreService service for reading Git objects
     * @param packFileEncoder    encoder for building pack files
     */
    public UploadPackServiceImpl(BranchRepository branchRepository,
                                 ObjectStoreService objectStoreService,
                                 PackFileEncoder packFileEncoder) {
        this.branchRepository = branchRepository;
        this.objectStoreService = objectStoreService;
        this.packFileEncoder = packFileEncoder;
    }

    // -------------------------------------------------------------------------
    // advertiseRefs
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Returns pkt-line encoded ref advertisement. Format:
     * <pre>
     *   pkt-line("{sha} refs/heads/{name}\0{capabilities}")  ← first ref
     *   pkt-line("{sha} refs/heads/{name}")                  ← subsequent refs
     *   0000                                                  ← flush-pkt
     * </pre>
     *
     * <p>If the repository has no branches, returns a capabilities advertisement:
     * <pre>
     *   pkt-line("{zero-sha} capabilities^{}\0{capabilities}")
     *   0000
     * </pre>
     */
    @Override
    public byte[] advertiseRefs(Long repoId) {
        List<Branch> branches = branchRepository.findByRepoId(repoId);

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        if (branches.isEmpty()) {
            // Empty repository: advertise capabilities only
            String capLine = ZERO_SHA + " capabilities^{}\0" + CAPABILITIES;
            out.writeBytes(PktLineUtil.encodeLine(capLine));
        } else {
            boolean first = true;
            for (Branch branch : branches) {
                String refName = "refs/heads/" + branch.getName();
                String sha = branch.getHeadSha();
                String line;
                if (first) {
                    line = sha + " " + refName + "\0" + CAPABILITIES;
                    first = false;
                } else {
                    line = sha + " " + refName;
                }
                out.writeBytes(PktLineUtil.encodeLine(line));
            }
        }

        // Flush-pkt
        out.writeBytes(PktLineUtil.FLUSH_PKT);

        return out.toByteArray();
    }

    // -------------------------------------------------------------------------
    // uploadPack
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Performs want/have negotiation:
     * <ol>
     *   <li>Parses pkt-line input for {@code want} and {@code have} lines.</li>
     *   <li>Walks the commit graph from each wanted SHA, stopping at have SHAs.</li>
     *   <li>Collects all reachable commit, tree, and blob SHAs.</li>
     *   <li>Builds a pack file via {@link PackFileEncoder}.</li>
     *   <li>Prepends {@code NAK\n} (no haves) or {@code ACK {sha} continue\n}
     *       lines (matched haves).</li>
     * </ol>
     */
    @Override
    public InputStream uploadPack(Long repoId, InputStream input) {
        // Parse want/have lines
        List<String> wants = new ArrayList<>();
        Set<String> haves = new LinkedHashSet<>();

        try {
            parseNegotiation(input, wants, haves);
        } catch (IOException e) {
            log.warn("Failed to parse upload-pack negotiation for repo {}: {}", repoId, e.getMessage());
            return new ByteArrayInputStream(new byte[0]);
        }

        if (wants.isEmpty()) {
            // Nothing requested — return empty response
            return new ByteArrayInputStream(PktLineUtil.FLUSH_PKT);
        }

        // Collect objects to send
        String repoIdStr = repoId.toString();
        Set<String> objectsToSend = collectObjects(repoIdStr, wants, haves);

        // Build pack file
        byte[] packBytes;
        try {
            packBytes = packFileEncoder.encode(repoIdStr, new ArrayList<>(objectsToSend));
        } catch (IOException e) {
            log.warn("Failed to encode pack for repo {}: {}", repoId, e.getMessage());
            packBytes = new byte[0];
        }

        // Build response: NAK or ACK lines + pack data
        ByteArrayOutputStream response = new ByteArrayOutputStream();

        if (haves.isEmpty()) {
            // No haves — send NAK
            response.writeBytes(PktLineUtil.encodeLine("NAK\n"));
        } else {
            // Send ACK for each have SHA that we know about
            for (String haveSha : haves) {
                if (objectExists(repoIdStr, haveSha)) {
                    response.writeBytes(PktLineUtil.encodeLine("ACK " + haveSha + " continue\n"));
                }
            }
            // If none matched, still send NAK
            if (response.size() == 0) {
                response.writeBytes(PktLineUtil.encodeLine("NAK\n"));
            }
        }

        response.writeBytes(packBytes);

        return new ByteArrayInputStream(response.toByteArray());
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Parses the pkt-line negotiation stream, populating {@code wants} and
     * {@code haves}.
     *
     * <p>Reads until a flush-pkt or {@code "done\n"} line is encountered.
     *
     * @param input the client's request body stream
     * @param wants list to populate with wanted SHAs
     * @param haves set to populate with have SHAs
     * @throws IOException if reading fails
     */
    private static void parseNegotiation(InputStream input,
                                         List<String> wants,
                                         Set<String> haves) throws IOException {
        String line;
        while ((line = PktLineUtil.readLine(input)) != null) {
            if (line.startsWith("want ")) {
                String sha = line.substring(5).trim();
                // Strip any capability string that may follow a NUL
                int nulIdx = sha.indexOf('\0');
                if (nulIdx >= 0) {
                    sha = sha.substring(0, nulIdx);
                }
                wants.add(sha);
            } else if (line.startsWith("have ")) {
                String sha = line.substring(5).trim();
                haves.add(sha);
            } else if (line.startsWith("done")) {
                break;
            }
            // ignore other lines (e.g. "shallow", "deepen", etc.)
        }
    }

    /**
     * Collects all object SHAs reachable from {@code wants} that are not
     * reachable from {@code haves}.
     *
     * <p>Walks the commit graph (BFS, depth-limited to {@link #MAX_COMMIT_WALK}),
     * then collects all tree and blob SHAs from each reachable commit's tree.
     *
     * @param repoId the repository ID string
     * @param wants  the list of wanted commit SHAs
     * @param haves  the set of have SHAs (client already has these)
     * @return ordered set of SHAs to include in the pack
     */
    private Set<String> collectObjects(String repoId,
                                       List<String> wants,
                                       Set<String> haves) {
        // BFS over commit graph
        Set<String> visitedCommits = new HashSet<>(haves);
        Set<String> commitsToSend = new LinkedHashSet<>();

        Deque<String> queue = new ArrayDeque<>(wants);
        int walkCount = 0;

        while (!queue.isEmpty() && walkCount < MAX_COMMIT_WALK) {
            String sha = queue.poll();
            if (visitedCommits.contains(sha)) {
                continue;
            }
            visitedCommits.add(sha);
            walkCount++;

            CommitObject commit = readCommit(repoId, sha);
            if (commit == null) {
                continue;
            }

            commitsToSend.add(sha);

            // Enqueue parents (stop at have SHAs)
            for (String parentSha : commit.getParentShas()) {
                if (!visitedCommits.contains(parentSha) && !haves.contains(parentSha)) {
                    queue.add(parentSha);
                }
            }
        }

        // Collect all objects: commits + their trees + blobs
        Set<String> allObjects = new LinkedHashSet<>();
        for (String commitSha : commitsToSend) {
            allObjects.add(commitSha);
            CommitObject commit = readCommit(repoId, commitSha);
            if (commit != null) {
                collectTreeObjects(repoId, commit.getTreeSha(), allObjects);
            }
        }

        return allObjects;
    }

    /**
     * Recursively collects all tree and blob SHAs reachable from the given
     * tree SHA.
     *
     * @param repoId    the repository ID string
     * @param treeSha   the root tree SHA to walk
     * @param collected the set to add discovered SHAs to
     */
    private void collectTreeObjects(String repoId, String treeSha, Set<String> collected) {
        if (treeSha == null || collected.contains(treeSha)) {
            return;
        }
        collected.add(treeSha);

        TreeObject tree = readTree(repoId, treeSha);
        if (tree == null) {
            return;
        }

        for (TreeEntry entry : tree.getEntries()) {
            String entrySha = entry.sha();
            if (collected.contains(entrySha)) {
                continue;
            }
            if ("040000".equals(entry.mode())) {
                // Sub-tree: recurse
                collectTreeObjects(repoId, entrySha, collected);
            } else {
                // Blob
                collected.add(entrySha);
            }
        }
    }

    /**
     * Reads and deserializes a {@link CommitObject} from the object store.
     *
     * @param repoId the repository ID string
     * @param sha    the commit SHA
     * @return the deserialized commit, or {@code null} if not found or on error
     */
    private CommitObject readCommit(String repoId, String sha) {
        try {
            byte[] raw = objectStoreService.readObject(repoId, sha);
            return parseCommit(raw);
        } catch (Exception e) {
            log.warn("Could not read commit {} in repo {}: {}", sha, repoId, e.getMessage());
            return null;
        }
    }

    /**
     * Reads and deserializes a {@link TreeObject} from the object store.
     *
     * @param repoId the repository ID string
     * @param sha    the tree SHA
     * @return the deserialized tree, or {@code null} if not found or on error
     */
    private TreeObject readTree(String repoId, String sha) {
        try {
            byte[] raw = objectStoreService.readObject(repoId, sha);
            return parseTree(raw);
        } catch (Exception e) {
            log.warn("Could not read tree {} in repo {}: {}", sha, repoId, e.getMessage());
            return null;
        }
    }

    /**
     * Checks whether an object exists in the object store without throwing.
     *
     * @param repoId the repository ID string
     * @param sha    the object SHA
     * @return {@code true} if the object can be read
     */
    private boolean objectExists(String repoId, String sha) {
        try {
            objectStoreService.readObject(repoId, sha);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // -------------------------------------------------------------------------
    // Object deserialization helpers
    // -------------------------------------------------------------------------

    /**
     * Parses raw commit bytes into a {@link CommitObject}.
     *
     * <p>The raw bytes have the format:
     * <pre>
     *   commit {size}\0tree {tree-sha}\n
     *   [parent {parent-sha}\n]...
     *   author {name} &lt;{email}&gt; {unix-ts} {tz}\n
     *   committer {name} &lt;{email}&gt; {unix-ts} {tz}\n
     *   \n
     *   {message}
     * </pre>
     *
     * @param raw the raw serialized commit bytes
     * @return the parsed {@link CommitObject}
     * @throws IllegalArgumentException if the bytes cannot be parsed as a commit
     */
    public static CommitObject parseCommit(byte[] raw) {
        String text = new String(raw, StandardCharsets.UTF_8);

        // Skip "commit {size}\0" header
        int nulIdx = text.indexOf('\0');
        if (nulIdx < 0) {
            throw new IllegalArgumentException("No NUL separator in commit object");
        }
        String body = text.substring(nulIdx + 1);
        String[] lines = body.split("\n", -1);

        String treeSha = null;
        List<String> parentShas = new ArrayList<>();
        String authorName = "";
        String authorEmail = "";
        long authorTimestamp = 0L;
        String committerName = "";
        String committerEmail = "";
        long committerTimestamp = 0L;
        StringBuilder message = new StringBuilder();

        boolean inMessage = false;
        for (String line : lines) {
            if (inMessage) {
                if (message.length() > 0) message.append("\n");
                message.append(line);
                continue;
            }
            if (line.isEmpty()) {
                inMessage = true;
                continue;
            }
            if (line.startsWith("tree ")) {
                treeSha = line.substring(5).trim();
            } else if (line.startsWith("parent ")) {
                parentShas.add(line.substring(7).trim());
            } else if (line.startsWith("author ")) {
                String[] parts = parsePersonLine(line.substring(7));
                authorName = parts[0];
                authorEmail = parts[1];
                authorTimestamp = Long.parseLong(parts[2]);
            } else if (line.startsWith("committer ")) {
                String[] parts = parsePersonLine(line.substring(10));
                committerName = parts[0];
                committerEmail = parts[1];
                committerTimestamp = Long.parseLong(parts[2]);
            }
        }

        if (treeSha == null) {
            throw new IllegalArgumentException("Commit object missing tree SHA");
        }

        return new CommitObject(
                treeSha, parentShas,
                authorName, authorEmail, authorTimestamp,
                committerName, committerEmail, committerTimestamp,
                message.toString());
    }

    /**
     * Parses a person line of the form:
     * {@code "{name} <{email}> {unix-ts} {tz}"}.
     *
     * @param personStr the person string (after the role prefix)
     * @return array of {@code [name, email, timestamp]}
     */
    private static String[] parsePersonLine(String personStr) {
        // Format: "Name <email> timestamp tz"
        int ltIdx = personStr.lastIndexOf('<');
        int gtIdx = personStr.lastIndexOf('>');
        String name = (ltIdx > 0) ? personStr.substring(0, ltIdx).trim() : "";
        String email = (ltIdx >= 0 && gtIdx > ltIdx)
                ? personStr.substring(ltIdx + 1, gtIdx) : "";
        String rest = (gtIdx >= 0) ? personStr.substring(gtIdx + 1).trim() : "";
        String[] restParts = rest.split("\\s+");
        String timestamp = (restParts.length > 0) ? restParts[0] : "0";
        return new String[]{name, email, timestamp};
    }

    /**
     * Parses raw tree bytes into a {@link TreeObject}.
     *
     * <p>The raw bytes have the format:
     * <pre>
     *   tree {size}\0{entries...}
     * </pre>
     * where each entry is: {@code "{mode} {name}\0{32-byte binary sha}"}.
     *
     * @param raw the raw serialized tree bytes
     * @return the parsed {@link TreeObject}
     * @throws IllegalArgumentException if the bytes cannot be parsed as a tree
     */
    public static TreeObject parseTree(byte[] raw) {
        // Find the NUL separator after "tree {size}"
        int nulIdx = -1;
        for (int i = 0; i < raw.length; i++) {
            if (raw[i] == 0) {
                nulIdx = i;
                break;
            }
        }
        if (nulIdx < 0) {
            throw new IllegalArgumentException("No NUL separator in tree object");
        }

        List<TreeEntry> entries = new ArrayList<>();
        int pos = nulIdx + 1;

        while (pos < raw.length) {
            // Read "{mode} {name}\0"
            int spaceIdx = -1;
            for (int i = pos; i < raw.length; i++) {
                if (raw[i] == ' ') {
                    spaceIdx = i;
                    break;
                }
            }
            if (spaceIdx < 0) break;

            String mode = new String(raw, pos, spaceIdx - pos, StandardCharsets.UTF_8);
            pos = spaceIdx + 1;

            // Read name until NUL
            int nameNulIdx = -1;
            for (int i = pos; i < raw.length; i++) {
                if (raw[i] == 0) {
                    nameNulIdx = i;
                    break;
                }
            }
            if (nameNulIdx < 0) break;

            String name = new String(raw, pos, nameNulIdx - pos, StandardCharsets.UTF_8);
            pos = nameNulIdx + 1;

            // Read 32-byte binary SHA (SHA-256 → 32 bytes)
            if (pos + 32 > raw.length) break;
            byte[] shaBytes = new byte[32];
            System.arraycopy(raw, pos, shaBytes, 0, 32);
            pos += 32;

            String sha = bytesToHex(shaBytes);
            entries.add(new TreeEntry(mode, name, sha));
        }

        return new TreeObject(entries);
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
