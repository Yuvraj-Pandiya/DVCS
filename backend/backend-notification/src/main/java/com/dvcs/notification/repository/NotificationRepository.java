package com.dvcs.notification.repository;

import com.dvcs.notification.domain.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link Notification} entities.
 */
@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /**
     * Returns a paginated list of unread notifications for a user, ordered by creation
     * date descending (newest first).
     *
     * @param userId   the ID of the user whose notifications to retrieve
     * @param pageable pagination parameters
     * @return a page of unread notifications
     */
    Page<Notification> findByUserIdAndReadFalseOrderByCreatedAtDesc(Long userId, Pageable pageable);
}
