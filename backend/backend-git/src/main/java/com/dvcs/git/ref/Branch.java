package com.dvcs.git.ref;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

/**
 * JPA entity mapping the {@code branches} table.
 *
 * <p>A branch is a named, mutable pointer to the tip commit of a line of
 * development within a repository.
 *
 * <p>Requirement 5: Branch and Tag Reference Management.
 */
@Entity
@Table(name = "branches")
public class Branch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** The repository this branch belongs to. */
    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    /** The branch name (e.g. {@code "main"}, {@code "feature/foo"}). */
    @Column(name = "name", nullable = false, length = 255)
    private String name;

    /** The SHA-256 hex digest of the commit at the tip of this branch. */
    @Column(name = "head_sha", nullable = false, length = 64)
    private String headSha;

    /** Whether this branch is protected against force-push and deletion. */
    @Column(name = "protected", nullable = false)
    private boolean protectedBranch;

    /** Timestamp when this branch was created. */
    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    /** Default constructor required by JPA. */
    protected Branch() {}

    /**
     * Constructs a {@code Branch} with all required fields.
     *
     * @param repoId          the repository ID
     * @param name            the branch name
     * @param headSha         the head commit SHA
     * @param protectedBranch whether the branch is protected
     * @param createdAt       creation timestamp
     */
    public Branch(Long repoId, String name, String headSha,
                  boolean protectedBranch, OffsetDateTime createdAt) {
        this.repoId = repoId;
        this.name = name;
        this.headSha = headSha;
        this.protectedBranch = protectedBranch;
        this.createdAt = createdAt;
    }

    public Long getId() { return id; }

    public Long getRepoId() { return repoId; }

    public String getName() { return name; }

    public String getHeadSha() { return headSha; }

    public boolean isProtectedBranch() { return protectedBranch; }

    public OffsetDateTime getCreatedAt() { return createdAt; }

    public void setHeadSha(String headSha) { this.headSha = headSha; }

    public void setProtectedBranch(boolean protectedBranch) {
        this.protectedBranch = protectedBranch;
    }
}
