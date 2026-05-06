package com.dvcs.repository.repository;

import com.dvcs.repository.domain.GitObject;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link GitObject} entities.
 */
@Repository
public interface GitObjectRepository extends JpaRepository<GitObject, Long> {

    /**
     * Computes the total storage size of all objects in a repository.
     *
     * @param repoId the repository ID
     * @return total size in bytes, or 0 if no objects exist
     */
    @Query("SELECT COALESCE(SUM(g.size), 0) FROM GitObject g WHERE g.repoId = :repoId")
    long sumSizeByRepoId(@Param("repoId") Long repoId);
}
