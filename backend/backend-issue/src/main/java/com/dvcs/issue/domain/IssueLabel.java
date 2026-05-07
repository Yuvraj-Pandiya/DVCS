package com.dvcs.issue.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * JPA entity representing the many-to-many join between issues and labels,
 * mapped to the {@code issue_labels} table.
 */
@Entity
@Table(name = "issue_labels")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IssueLabel {

    @EmbeddedId
    private IssueLabelId id;

    /**
     * Composite primary key for {@link IssueLabel}.
     */
    @Embeddable
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IssueLabelId implements Serializable {

        @Column(name = "issue_id", nullable = false)
        private Long issueId;

        @Column(name = "label_id", nullable = false)
        private Long labelId;
    }
}
