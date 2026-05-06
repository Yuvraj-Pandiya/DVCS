package com.dvcs.repository.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Composite primary key class for the {@link Collaborator} entity,
 * representing the {@code (repo_id, user_id)} primary key of the
 * {@code collaborators} table.
 */
public class CollaboratorId implements Serializable {

    private Long repoId;
    private Long userId;

    public CollaboratorId() {
    }

    public CollaboratorId(Long repoId, Long userId) {
        this.repoId = repoId;
        this.userId = userId;
    }

    public Long getRepoId() {
        return repoId;
    }

    public void setRepoId(Long repoId) {
        this.repoId = repoId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CollaboratorId that)) return false;
        return Objects.equals(repoId, that.repoId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(repoId, userId);
    }
}
