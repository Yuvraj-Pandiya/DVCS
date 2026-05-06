package com.dvcs.repository.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing a Git object (blob, tree, or commit),
 * mapped to the {@code git_objects} table.
 */
@Entity
@Table(name = "git_objects")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GitObject {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "sha", nullable = false, length = 64)
    private String sha;

    @Column(name = "type", nullable = false, length = 8)
    private String type;

    @Column(name = "size", nullable = false)
    private Long size;

    @Column(name = "stored_path", nullable = false, length = 512)
    private String storedPath;
}
