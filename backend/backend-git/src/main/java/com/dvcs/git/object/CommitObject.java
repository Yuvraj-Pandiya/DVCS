package com.dvcs.git.object;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;

/**
 * Represents a Git commit object — a snapshot that references a root tree,
 * zero or more parent commits, author/committer metadata, and a commit message.
 *
 * <p>Serialization format:
 * <pre>
 *   commit {size}\0tree {tree-sha}\n
 *   [parent {parent-sha}\n]...
 *   author {name} &lt;{email}&gt; {unix-ts} {tz}\n
 *   committer {name} &lt;{email}&gt; {unix-ts} {tz}\n
 *   \n
 *   {message}
 * </pre>
 * where {@code {size}} is the byte length of the body (everything after the NUL),
 * and the timezone offset is always {@code +0000}.
 *
 * <p>The SHA-256 hash is computed from the full serialized form and stored
 * automatically in the parent class.
 *
 * <p>Requirement 4: Git Object Storage Engine — commit serialization and SHA-256 hashing.
 */
public class CommitObject extends GitObject {

    /** Fixed timezone offset used in all serialized commit headers. */
    private static final String TIMEZONE = "+0000";

    private final String treeSha;
    private final List<String> parentShas;
    private final String authorName;
    private final String authorEmail;
    private final long authorTimestamp;
    private final String committerName;
    private final String committerEmail;
    private final long committerTimestamp;
    private final String message;

    /**
     * Constructs a {@code CommitObject} with all required commit metadata.
     *
     * <p>The SHA-256 hash is computed from the full serialized form and stored
     * in the parent class.
     *
     * @param treeSha            the SHA-256 hex digest of the root tree object; must not be {@code null}
     * @param parentShas         list of parent commit SHA-256 hex digests; must not be {@code null},
     *                           may be empty for root commits
     * @param authorName         the author's display name; must not be {@code null}
     * @param authorEmail        the author's email address; must not be {@code null}
     * @param authorTimestamp    the author timestamp as Unix epoch seconds
     * @param committerName      the committer's display name; must not be {@code null}
     * @param committerEmail     the committer's email address; must not be {@code null}
     * @param committerTimestamp the committer timestamp as Unix epoch seconds
     * @param message            the commit message; must not be {@code null}
     * @throws NullPointerException if any parameter is {@code null}
     */
    public CommitObject(
            String treeSha,
            List<String> parentShas,
            String authorName,
            String authorEmail,
            long authorTimestamp,
            String committerName,
            String committerEmail,
            long committerTimestamp,
            String message) {

        super(computeSha(
                Objects.requireNonNull(treeSha,            "treeSha must not be null"),
                Objects.requireNonNull(parentShas,         "parentShas must not be null"),
                Objects.requireNonNull(authorName,         "authorName must not be null"),
                Objects.requireNonNull(authorEmail,        "authorEmail must not be null"),
                authorTimestamp,
                Objects.requireNonNull(committerName,      "committerName must not be null"),
                Objects.requireNonNull(committerEmail,     "committerEmail must not be null"),
                committerTimestamp,
                Objects.requireNonNull(message,            "message must not be null")),
              ObjectType.COMMIT);

        this.treeSha            = treeSha;
        this.parentShas         = List.copyOf(parentShas);
        this.authorName         = authorName;
        this.authorEmail        = authorEmail;
        this.authorTimestamp    = authorTimestamp;
        this.committerName      = committerName;
        this.committerEmail     = committerEmail;
        this.committerTimestamp = committerTimestamp;
        this.message            = message;
    }

    /**
     * Returns the canonical byte encoding of this commit object.
     *
     * <p>Format:
     * <pre>
     *   commit {size}\0tree {tree-sha}\n
     *   [parent {parent-sha}\n]...
     *   author {name} &lt;{email}&gt; {unix-ts} +0000\n
     *   committer {name} &lt;{email}&gt; {unix-ts} +0000\n
     *   \n
     *   {message}
     * </pre>
     *
     * @return canonical byte array; never {@code null}
     */
    @Override
    public byte[] serialize() {
        byte[] body   = buildBody(treeSha, parentShas, authorName, authorEmail,
                                  authorTimestamp, committerName, committerEmail,
                                  committerTimestamp, message);
        byte[] header = buildHeader(body.length);
        byte[] result = new byte[header.length + body.length];
        System.arraycopy(header, 0, result, 0, header.length);
        System.arraycopy(body,   0, result, header.length, body.length);
        return result;
    }

    // -------------------------------------------------------------------------
    // Accessors
    // -------------------------------------------------------------------------

    /** @return the SHA-256 hex digest of the root tree object */
    public String getTreeSha() { return treeSha; }

    /** @return an unmodifiable list of parent commit SHA-256 hex digests */
    public List<String> getParentShas() { return parentShas; }

    /** @return the author's display name */
    public String getAuthorName() { return authorName; }

    /** @return the author's email address */
    public String getAuthorEmail() { return authorEmail; }

    /** @return the author timestamp as Unix epoch seconds */
    public long getAuthorTimestamp() { return authorTimestamp; }

    /** @return the committer's display name */
    public String getCommitterName() { return committerName; }

    /** @return the committer's email address */
    public String getCommitterEmail() { return committerEmail; }

    /** @return the committer timestamp as Unix epoch seconds */
    public long getCommitterTimestamp() { return committerTimestamp; }

    /** @return the commit message */
    public String getMessage() { return message; }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Builds the commit body bytes (everything after the {@code "commit {size}\0"} header).
     *
     * <p>The body consists of:
     * <ul>
     *   <li>{@code tree {tree-sha}\n}</li>
     *   <li>zero or more {@code parent {parent-sha}\n} lines</li>
     *   <li>{@code author {name} <{email}> {unix-ts} +0000\n}</li>
     *   <li>{@code committer {name} <{email}> {unix-ts} +0000\n}</li>
     *   <li>a blank line {@code \n}</li>
     *   <li>the commit message (UTF-8)</li>
     * </ul>
     */
    private static byte[] buildBody(
            String treeSha,
            List<String> parentShas,
            String authorName,
            String authorEmail,
            long authorTimestamp,
            String committerName,
            String committerEmail,
            long committerTimestamp,
            String message) {

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            // tree line
            out.write(("tree " + treeSha + "\n").getBytes(StandardCharsets.UTF_8));

            // parent lines (zero or more)
            for (String parentSha : parentShas) {
                out.write(("parent " + parentSha + "\n").getBytes(StandardCharsets.UTF_8));
            }

            // author line
            out.write(formatPersonLine("author", authorName, authorEmail, authorTimestamp)
                    .getBytes(StandardCharsets.UTF_8));

            // committer line
            out.write(formatPersonLine("committer", committerName, committerEmail, committerTimestamp)
                    .getBytes(StandardCharsets.UTF_8));

            // blank line separating headers from message
            out.write("\n".getBytes(StandardCharsets.UTF_8));

            // commit message
            out.write(message.getBytes(StandardCharsets.UTF_8));

        } catch (IOException e) {
            // ByteArrayOutputStream never throws IOException
            throw new IllegalStateException("Unexpected I/O error", e);
        }
        return out.toByteArray();
    }

    /**
     * Formats a person line in the form:
     * {@code "{role} {name} <{email}> {unix-ts} +0000\n"}.
     *
     * @param role      either {@code "author"} or {@code "committer"}
     * @param name      the person's display name
     * @param email     the person's email address
     * @param timestamp Unix epoch seconds
     * @return the formatted line including the trailing newline
     */
    private static String formatPersonLine(String role, String name, String email, long timestamp) {
        return role + " " + name + " <" + email + "> " + timestamp + " " + TIMEZONE + "\n";
    }

    /**
     * Builds the commit header bytes: {@code "commit {size}\0"} encoded as UTF-8.
     *
     * @param bodySize the byte length of the commit body
     * @return header bytes
     */
    private static byte[] buildHeader(int bodySize) {
        return ("commit " + bodySize + "\0").getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Computes the SHA-256 hex digest of the serialized commit
     * ({@code "commit {size}\0"} + body bytes).
     *
     * @return 64-character lowercase hex SHA-256 string
     */
    private static String computeSha(
            String treeSha,
            List<String> parentShas,
            String authorName,
            String authorEmail,
            long authorTimestamp,
            String committerName,
            String committerEmail,
            long committerTimestamp,
            String message) {

        byte[] body   = buildBody(treeSha, parentShas, authorName, authorEmail,
                                  authorTimestamp, committerName, committerEmail,
                                  committerTimestamp, message);
        byte[] header = buildHeader(body.length);
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(header);
            digest.update(body);
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
