package com.dvcs.issue.repository;

import com.dvcs.issue.domain.IssueLabel;
import com.dvcs.issue.domain.IssueLabel.IssueLabelId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Spring Data JPA repository for {@link IssueLabel} join entities.
 */
@Repository
public interface IssueLabelRepository extends JpaRepository<IssueLabel, IssueLabelId> {

    /**
     * Returns all issue-label associations for a given issue.
     *
     * @param issueId the issue ID
     * @return list of {@link IssueLabel} records for the issue
     */
    List<IssueLabel> findByIdIssueId(Long issueId);

    /**
     * Removes a specific label from an issue.
     *
     * @param issueId the issue ID
     * @param labelId the label ID
     */
    @Transactional
    void deleteByIdIssueIdAndIdLabelId(Long issueId, Long labelId);
}
