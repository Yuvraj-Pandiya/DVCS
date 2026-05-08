package com.dvcs.auth.repository;

import com.dvcs.auth.domain.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link User} entities.
 *
 * <p>Derived query methods are resolved automatically by Spring Data JPA
 * based on the field names declared in {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Looks up a user by their unique username.
     *
     * @param username the username to search for
     * @return an {@link Optional} containing the matching user, or empty if none found
     */
    Optional<User> findByUsername(String username);

    /**
     * Looks up a user by their unique email address.
     *
     * @param email the email address to search for
     * @return an {@link Optional} containing the matching user, or empty if none found
     */
    Optional<User> findByEmail(String email);

    /**
     * Searches users by username or bio.
     */
    @org.springframework.data.jpa.repository.Query("SELECT u FROM User u WHERE LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(u.bio) LIKE LOWER(CONCAT('%', :query, '%'))")
    org.springframework.data.domain.Page<User> searchUsers(@org.springframework.data.repository.query.Param("query") String query, org.springframework.data.domain.Pageable pageable);
}
