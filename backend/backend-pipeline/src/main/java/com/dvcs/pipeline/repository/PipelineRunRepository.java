package com.dvcs.pipeline.repository;

import com.dvcs.pipeline.domain.PipelineRun;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link PipelineRun} entities.
 *
 * <p>Requirement 13: CI/CD Pipeline Simulation.
 */
@Repository
public interface PipelineRunRepository extends JpaRepository<PipelineRun, Long> {

    /**
     * Finds all pipeline runs for a repository ordered by creation date descending (newest first).
     *
     * @param repoId   the repository ID
     * @param pageable pagination parameters
     * @return page of pipeline runs
     */
    Page<PipelineRun> findByRepoIdOrderByCreatedAtDesc(Long repoId, Pageable pageable);

    /**
     * Finds all pipeline runs for a repository and commit SHA.
     *
     * @param repoId    the repository ID
     * @param commitSha the commit SHA
     * @return list of pipeline runs for the given commit
     */
    List<PipelineRun> findByRepoIdAndCommitSha(Long repoId, String commitSha);
}
