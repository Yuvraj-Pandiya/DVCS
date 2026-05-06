package com.dvcs.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a collaborator role on a repository,
 * mapped to the {@code collaborators} table.
 *
 * <p>The composite primary key is {@code (repo_id, user_id)}.
 * The {@code role} column holds one of {@code OWNER}, {@code WRITE}, or {@code READ}.
 */
@Entity
@Table(name = "collaborators")
@IdClass(CollaboratorId.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Collaborator {

    @Id
    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Id
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * The collaborator's role. One of {@code OWNER}, {@code WRITE}, or {@code READ}.
     */
    @Column(name = "role", nullable = false, length = 16)
    private String role;
}
