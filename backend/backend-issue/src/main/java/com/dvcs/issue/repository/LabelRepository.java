package com.dvcs.issue.repository;

import com.dvcs.issue.domain.Label;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Label} entities.
 */
@Repository
public interface LabelRepository extends JpaRepository<Label, Long> {

    /**
     * Finds a label by repository ID and label name.
     *
     * @param repoId the repository ID
     * @param name   the label name
     * @return an {@link Optional} containing the label, or empty if not found
     */
    Optional<Label> findByRepoIdAndName(Long repoId, String name);

    /**
     * Returns all labels belonging to a repository.
     *
     * @param repoId the repository ID
     * @return list of labels for the repository
     */
    List<Label> findByRepoId(Long repoId);
}
