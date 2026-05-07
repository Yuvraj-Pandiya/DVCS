package com.dvcs.pullrequest.repository;

import com.dvcs.pullrequest.domain.PrComment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PrComment} entities.
 */
@Repository
public interface PrCommentRepository extends JpaRepository<PrComment, Long> {

    /**
     * Finds all comments for a pull request, ordered by creation time ascending.
     *
     * @param prId the pull request ID
     * @return list of comments ordered oldest-first
     */
    List<PrComment> findByPrIdOrderByCreatedAtAsc(Long prId);
}
