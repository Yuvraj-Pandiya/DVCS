package com.dvcs.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JPA entity representing a label, mapped to the {@code labels} table.
 *
 * <p>Labels are scoped to a repository and can be applied to issues for categorisation.
 * Each label has a unique name within its repository and a hex colour code.
 */
@Entity
@Table(
        name = "labels",
        uniqueConstraints = @UniqueConstraint(
                name = "uq_labels_repo_name",
                columnNames = {"repo_id", "name"}
        )
)
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Label {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "repo_id", nullable = false)
    private Long repoId;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    /**
     * Hex colour code for the label, e.g. {@code #ff0000}.
     */
    @Column(name = "color", nullable = false, columnDefinition = "CHAR(7)")
    private String color;
}
