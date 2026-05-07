package com.dvcs.issue.repository;

import com.dvcs.issue.domain.IssueComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link IssueComment} entities.
 */
@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {

    /**
     * Finds all comments for a given issue, ordered by creation time ascending.
     *
     * @param issueId the issue ID
     * @return list of comments in chronological order
     */
    List<IssueComment> findByIssueIdOrderByCreatedAtAsc(Long issueId);
}
