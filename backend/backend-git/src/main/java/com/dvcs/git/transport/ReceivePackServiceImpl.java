package com.dvcs.git.transport;

import com.dvcs.common.audit.Audited;
import com.dvcs.git.commit.CommitMeta;
import com.dvcs.git.commit.CommitMetaRepository;
import com.dvcs.git.event.PushEvent;
import com.dvcs.git.object.BlobObject;
import com.dvcs.git.object.CommitObject;
import com.dvcs.git.object.ObjectType;
import com.dvcs.git.object.TreeEntry;
import com.dvcs.git.object.TreeObject;
import com.dvcs.git.pack.PackFileDecoder;
import com.dvcs.git.pack.RawObject;
import com.dvcs.git.ref.Branch;
import com.dvcs.git.ref.BranchRepository;
import com.dvcs.git.storage.ObjectStoreService;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Full implementation of {@link ReceivePackService} for the Git smart HTTP
 * receive-pack protocol (push).
 *
 * <h2>advertiseRefs</h2>
 * <p>Returns a pkt-line encoded ref advertisement listing all branches in the
 * repository. The first ref line includes a NUL-separated capability string.
 * An empty repository returns a special capabilities advertisement.
 *
 * <h2>receivePack</h2>
 * <p>Processes an incoming push:
 * <ol>
 *   <li>Parses pkt-line ref updates from the request body.</li>
 *   <li>Reads the remaining pack data and decodes it via {@link PackFileDecoder}.</li>
 *   <li>For each ref update: checks branch protection, writes objects to the
 *       object store, updates the branch head SHA, and inserts commit metadata rows.</li>
 *   <li>Invalidates Redis cache keys for the affected repository.</li>
 *   <li>Publishes a push event to the Redis pub/sub channel {@code events:{repoId}}.</li>
 *   <li>Publishes a Spring {@link PushEvent} for async webhook and pipeline processing.</li>
 * </ol>
 *
 * <p>Requirement 6: HTTP Smart Git Transport — receive-pack protocol.
 */
@Service
@Primary
public class ReceivePackServiceImpl implements ReceivePackService {

    private static final Logger log = LoggerFactory.getLogger(ReceivePackServiceImpl.class);

    /** Capabilities advertised to the client during receive-pack ref advertisement. */
    private static final String CAPABILITIES =
            "report-status delete-refs side-band-64k ofs-delta";

    /** Zero SHA used in the empty-repo capabilities advertisement. */
    private static final String ZERO_SHA =
            "0000000000000000000000000000000000000000000000000000000000000000";

    /** Prefix for refs/heads/ references. */
    private static final String REFS_HEADS_PREFIX = "refs/heads/";

    private final BranchRepository branchRepository;
    private final ObjectStoreService objectStoreService;
    private final PackFileDecoder packFileDecoder;
    private final CommitMetaRepository commitMetaRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final RedisTemplate<String, String> redisTemplate;
    private final ApplicationEventPublisher eventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Constructs a {@code ReceivePackServiceImpl}.
     *
     * @param branchRepository      repository for branch lookups and updates
     * @param objectStoreService    service for writing Git objects
     * @param packFileDecoder       decoder for incoming pack-file streams
     * @param commitMetaRepository  repository for commit metadata persistence
     * @param stringRedisTemplate   Redis template for pub/sub publishing
     * @param redisTemplate         Redis template for cache key invalidation
     * @param eventPublisher        Spring event publisher for async downstream processing
     * @param objectMapper          Jackson mapper for JSON serialization of push events
     */
    public ReceivePackServiceImpl(BranchRepository branchRepository,
                                  ObjectStoreService objectStoreService,
                                  PackFileDecoder packFileDecoder,
                                  CommitMetaRepository commitMetaRepository,
                                  StringRedisTemplate stringRedisTemplate,
                                  RedisTemplate<String, String> redisTemplate,
                                  ApplicationEventPublisher eventPublisher,
                                  ObjectMapper objectMapper) {
        this.branchRepository = branchRepository;
        this.objectStoreService = objectStoreService;
        this.packFileDecoder = packFileDecoder;
        this.commitMetaRepository = commitMetaRepository;
        this.stringRedisTemplate = stringRedisTemplate;
        this.redisTemplate = redisTemplate;
        this.eventPublisher = eventPublisher;
        this.objectMapper = objectMapper;
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
                String refName = REFS_HEADS_PREFIX + branch.getName();
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
    // receivePack
    // -------------------------------------------------------------------------

    /**
     * {@inheritDoc}
     *
     * <p>Processing steps:
     * <ol>
     *   <li>Parse pkt-line ref updates until flush-pkt.</li>
     *   <li>Read remaining bytes as pack data and decode via {@link PackFileDecoder}.</li>
     *   <li>For each ref update:
     *     <ul>
     *       <li>Check branch protection.</li>
     *       <li>Write all decoded objects to the object store.</li>
     *       <li>Update or create the branch head SHA.</li>
     *       <li>Insert {@link CommitMeta} rows for new commit objects.</li>
     *     </ul>
     *   </li>
     *   <li>Invalidate Redis cache keys.</li>
     *   <li>Publish push event to Redis pub/sub.</li>
     *   <li>Publish Spring {@link PushEvent} for async webhook and pipeline processing.</li>
     * </ol>
     */
    @Override
    @Transactional
    @Audited(action = "push", resourceType = "repository")
    public void receivePack(Long repoId, Long userId, InputStream input) throws IOException {
        // Step 1: Parse ref updates from pkt-line stream
        List<RefUpdate> refUpdates = parseRefUpdates(input);

        if (refUpdates.isEmpty()) {
            log.debug("No ref updates in receive-pack for repo {}", repoId);
            return;
        }

        // Step 2: Read remaining pack data and decode
        byte[] packData = readRemainingBytes(input);
        List<RawObject> rawObjects = new ArrayList<>();
        if (packData.length > 0) {
            try {
                rawObjects = packFileDecoder.decode(
                        new java.io.ByteArrayInputStream(packData));
            } catch (Exception e) {
                log.warn("Failed to decode pack for repo {}: {}", repoId, e.getMessage());
                throw new IOException("Failed to decode pack data: " + e.getMessage(), e);
            }
        }

        String repoIdStr = repoId.toString();

        // Step 3: Process each ref update
        for (RefUpdate update : refUpdates) {
            String branchName = extractBranchName(update.refName());
            if (branchName == null) {
                log.warn("Skipping non-branch ref update: {}", update.refName());
                continue;
            }

            // 3a. Check branch protection
            Optional<Branch> existingBranch =
                    branchRepository.findByRepoIdAndName(repoId, branchName);
            if (existingBranch.isPresent() && existingBranch.get().isProtectedBranch()) {
                throw new ProtectedBranchException(branchName);
            }

            // 3b. Write all decoded objects to the object store
            List<String> newCommitShas = new ArrayList<>();
            for (RawObject rawObject : rawObjects) {
                writeRawObject(repoIdStr, rawObject);
                if (rawObject.type() == ObjectType.COMMIT) {
                    newCommitShas.add(rawObject.sha());
                }
            }

            // 3c. Update or create branch
            Branch branch;
            if (existingBranch.isPresent()) {
                branch = existingBranch.get();
                branch.setHeadSha(update.newSha());
            } else {
                branch = new Branch(repoId, branchName, update.newSha(),
                        false, OffsetDateTime.now(ZoneOffset.UTC));
            }
            branchRepository.save(branch);

            // 3d. Insert CommitMeta rows for new commit objects
            for (RawObject rawObject : rawObjects) {
                if (rawObject.type() == ObjectType.COMMIT) {
                    persistCommitMeta(repoId, rawObject);
                }
            }

            // Step 4: Invalidate Redis cache
            invalidateCache(repoId, branchName);

            // Step 5: Publish push event to Redis pub/sub
            PushEvent pushEvent = new PushEvent(repoId, userId, branchName,
                    update.newSha(), newCommitShas);
            publishToRedis(repoId, pushEvent);

            // Steps 6 & 7: Publish Spring event for async webhook and pipeline processing
            eventPublisher.publishEvent(pushEvent);

            log.info("Push accepted: repo={} branch={} newHead={} objects={}",
                    repoId, branchName, update.newSha(), rawObjects.size());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — parsing
    // -------------------------------------------------------------------------

    /**
     * Parses pkt-line ref update lines from the input stream until a flush-pkt.
     *
     * <p>Each line has the format: {@code "{old-sha} {new-sha} {refname}\n"}.
     *
     * @param input the request body stream
     * @return list of parsed ref updates
     * @throws IOException if reading fails
     */
    private static List<RefUpdate> parseRefUpdates(InputStream input) throws IOException {
        List<RefUpdate> updates = new ArrayList<>();
        String line;
        while ((line = PktLineUtil.readLine(input)) != null) {
            // Strip trailing newline
            if (line.endsWith("\n")) {
                line = line.substring(0, line.length() - 1);
            }
            // Strip any capability string after NUL (first line only)
            int nulIdx = line.indexOf('\0');
            if (nulIdx >= 0) {
                line = line.substring(0, nulIdx);
            }
            if (line.isBlank()) {
                continue;
            }
            String[] parts = line.split(" ", 3);
            if (parts.length < 3) {
                log.warn("Ignoring malformed ref update line: '{}'", line);
                continue;
            }
            updates.add(new RefUpdate(parts[0], parts[1], parts[2]));
        }
        return updates;
    }

    /**
     * Reads all remaining bytes from the input stream.
     *
     * @param input the stream to drain
     * @return all remaining bytes
     * @throws IOException if reading fails
     */
    private static byte[] readRemainingBytes(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = input.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Extracts the short branch name from a full ref name.
     *
     * <p>Returns {@code null} for refs that are not under {@code refs/heads/}.
     *
     * @param refName the full ref name (e.g. {@code "refs/heads/main"})
     * @return the short branch name, or {@code null} if not a branch ref
     */
    private static String extractBranchName(String refName) {
        if (refName != null && refName.startsWith(REFS_HEADS_PREFIX)) {
            return refName.substring(REFS_HEADS_PREFIX.length());
        }
        return null;
    }

    // -------------------------------------------------------------------------
    // Private helpers — object writing
    // -------------------------------------------------------------------------

    /**
     * Reconstructs the appropriate {@link com.dvcs.git.object.GitObject} subtype
     * from a {@link RawObject} and writes it to the object store.
     *
     * @param repoId    the repository ID string
     * @param rawObject the decoded raw object
     * @throws IOException if writing fails
     */
    private void writeRawObject(String repoId, RawObject rawObject) throws IOException {
        try {
            switch (rawObject.type()) {
                case BLOB -> {
                    byte[] content = extractBlobContent(rawObject.data());
                    objectStoreService.writeObject(repoId, new BlobObject(content));
                }
                case TREE -> {
                    List<TreeEntry> entries = UploadPackServiceImpl.parseTree(rawObject.data()).getEntries();
                    objectStoreService.writeObject(repoId, new TreeObject(entries));
                }
                case COMMIT -> {
                    CommitObject commit = UploadPackServiceImpl.parseCommit(rawObject.data());
                    objectStoreService.writeObject(repoId, new CommitObject(
                            commit.getTreeSha(),
                            commit.getParentShas(),
                            commit.getAuthorName(),
                            commit.getAuthorEmail(),
                            commit.getAuthorTimestamp(),
                            commit.getCommitterName(),
                            commit.getCommitterEmail(),
                            commit.getCommitterTimestamp(),
                            commit.getMessage()));
                }
            }
        } catch (Exception e) {
            log.warn("Failed to write object {} (type={}) to repo {}: {}",
                    rawObject.sha(), rawObject.type(), repoId, e.getMessage());
            throw new IOException("Failed to write object " + rawObject.sha() + ": " + e.getMessage(), e);
        }
    }

    /**
     * Extracts the raw content bytes from a blob's serialized form by stripping
     * the {@code "blob {size}\0"} header.
     *
     * @param rawData the full serialized blob bytes
     * @return the content bytes after the NUL separator
     */
    private static byte[] extractBlobContent(byte[] rawData) {
        // Find the NUL separator after "blob {size}"
        for (int i = 0; i < rawData.length; i++) {
            if (rawData[i] == 0) {
                int contentLen = rawData.length - i - 1;
                byte[] content = new byte[contentLen];
                System.arraycopy(rawData, i + 1, content, 0, contentLen);
                return content;
            }
        }
        // No header found — treat entire data as content
        return rawData;
    }

    // -------------------------------------------------------------------------
    // Private helpers — commit metadata
    // -------------------------------------------------------------------------

    /**
     * Parses a commit {@link RawObject} and persists a {@link CommitMeta} row,
     * skipping duplicates (idempotent on re-push).
     *
     * @param repoId    the repository ID
     * @param rawObject the raw commit object
     */
    private void persistCommitMeta(Long repoId, RawObject rawObject) {
        // Skip if already persisted (idempotent)
        if (commitMetaRepository.findByRepoIdAndSha(repoId, rawObject.sha()).isPresent()) {
            return;
        }

        try {
            CommitObject commit = UploadPackServiceImpl.parseCommit(rawObject.data());

            OffsetDateTime authoredAt = epochToOffsetDateTime(commit.getAuthorTimestamp());
            OffsetDateTime committedAt = epochToOffsetDateTime(commit.getCommitterTimestamp());

            CommitMeta meta = new CommitMeta(
                    repoId,
                    rawObject.sha(),
                    null,           // authorId: not resolved here (no user lookup)
                    commit.getMessage(),
                    authoredAt,
                    committedAt);

            commitMetaRepository.save(meta);
        } catch (Exception e) {
            log.warn("Failed to persist commit meta for sha={} repo={}: {}",
                    rawObject.sha(), repoId, e.getMessage());
        }
    }

    /**
     * Converts a Unix epoch seconds timestamp to an {@link OffsetDateTime} in UTC.
     *
     * @param epochSeconds Unix epoch seconds
     * @return UTC {@link OffsetDateTime}
     */
    private static OffsetDateTime epochToOffsetDateTime(long epochSeconds) {
        return OffsetDateTime.ofInstant(Instant.ofEpochSecond(epochSeconds), ZoneOffset.UTC);
    }

    // -------------------------------------------------------------------------
    // Private helpers — cache invalidation
    // -------------------------------------------------------------------------

    /**
     * Invalidates Redis cache keys for the given repository and branch:
     * <ul>
     *   <li>{@code repo:{repoId}:branches}</li>
     *   <li>{@code repo:{repoId}:commits:{branchName}:*} (via SCAN + DEL)</li>
     * </ul>
     *
     * @param repoId     the repository ID
     * @param branchName the branch name
     */
    private void invalidateCache(Long repoId, String branchName) {
        try {
            // Delete branch list cache
            redisTemplate.delete("repo:" + repoId + ":branches");

            // SCAN + DEL for commit log pages
            String pattern = "repo:" + repoId + ":commits:" + branchName + ":*";
            List<String> keysToDelete = new ArrayList<>();

            try (var cursor = redisTemplate.scan(
                    ScanOptions.scanOptions().match(pattern).count(100).build())) {
                while (cursor.hasNext()) {
                    keysToDelete.add(cursor.next());
                }
            } catch (Exception e) {
                log.warn("SCAN failed for pattern {}: {}", pattern, e.getMessage());
            }

            if (!keysToDelete.isEmpty()) {
                redisTemplate.delete(keysToDelete);
            }
        } catch (Exception e) {
            log.warn("Cache invalidation failed for repo={} branch={}: {}",
                    repoId, branchName, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers — Redis pub/sub
    // -------------------------------------------------------------------------

    /**
     * Serializes the {@link PushEvent} to JSON and publishes it to the Redis
     * pub/sub channel {@code events:{repoId}}.
     *
     * @param repoId    the repository ID
     * @param pushEvent the push event to publish
     */
    private void publishToRedis(Long repoId, PushEvent pushEvent) {
        try {
            String json = objectMapper.writeValueAsString(pushEvent);
            stringRedisTemplate.convertAndSend("events:" + repoId, json);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize push event for repo {}: {}", repoId, e.getMessage());
        } catch (Exception e) {
            log.warn("Failed to publish push event to Redis for repo {}: {}", repoId, e.getMessage());
        }
    }

    // -------------------------------------------------------------------------
    // Internal record types
    // -------------------------------------------------------------------------

    /**
     * Represents a single ref update parsed from the pkt-line stream.
     *
     * @param oldSha  the old commit SHA (all zeros for a new branch)
     * @param newSha  the new commit SHA
     * @param refName the full ref name (e.g. {@code "refs/heads/main"})
     */
    private record RefUpdate(String oldSha, String newSha, String refName) {}
}
