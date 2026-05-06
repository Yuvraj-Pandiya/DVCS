package com.dvcs.git.commit;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * JPA entity mapping the {@code commits_meta} table.
 *
 * <p>Stores lightweight commit metadata extracted from pushed commit objects,
 * enabling fast commit-log queries without reading raw Git objects from the
 * object store.
 *
 * <p>Requirement 6: HTTP Smart Git Transport — commit metadata persistence on push.
 */
@Entity
@Table(name = "commits_meta")
public class CommitMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The repository this commit belongs to. */
    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    /** The SHA-256 hex digest of the commit object. */
    @Column(name = "sha", nullable = false, length = 64)
    private String sha;

    /**
     * The ID of the user who authored the commit, if resolvable.
     * May be {@code null} when the author email does not match any registered user.
     */
    @Column(name = "author_id")
    private Long authorId;

    /** The commit message. */
    @Column(name = "message", nullable = false, columnDefinition = "TEXT")
    private String message;

    /** The author timestamp extracted from the commit object. */
    @Column(name = "authored_at", nullable = false)
    private OffsetDateTime authoredAt;

    /** The committer timestamp extracted from the commit object. */
    @Column(name = "committed_at", nullable = false)
    private OffsetDateTime committedAt;

    /** Default constructor required by JPA. */
    protected CommitMeta() {}

    /**
     * Constructs a {@code CommitMeta} with all required fields.
     *
     * @param repoId      the repository ID
     * @param sha         the commit SHA-256 hex digest
     * @param authorId    the author user ID (may be {@code null})
     * @param message     the commit message
     * @param authoredAt  the author timestamp
     * @param committedAt the committer timestamp
     */
    public CommitMeta(Long repoId, String sha, Long authorId, String message,
                      OffsetDateTime authoredAt, OffsetDateTime committedAt) {
        this.repoId = repoId;
        this.sha = sha;
        this.authorId = authorId;
        this.message = message;
        this.authoredAt = authoredAt;
        this.committedAt = committedAt;
    }

    public Long getId() { return id; }

    public Long getRepoId() { return repoId; }

    public String getSha() { return sha; }

    public Long getAuthorId() { return authorId; }

    public String getMessage() { return message; }

    public OffsetDateTime getAuthoredAt() { return authoredAt; }

    public OffsetDateTime getCommittedAt() { return committedAt; }
}
