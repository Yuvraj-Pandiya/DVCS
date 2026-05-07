package com.dvcs.notification.service;

import com.dvcs.notification.domain.Notification;
import com.dvcs.notification.repository.NotificationRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service responsible for creating notification records and publishing them to Redis
 * for real-time fan-out via WebSocket/STOMP.
 *
 * <p>When a notification is created:
 * <ol>
 *   <li>A {@link Notification} record is persisted to the database.</li>
 *   <li>The notification is serialized to JSON and published to the Redis channel
 *       {@code events:{userId}} for fan-out to all server instances.</li>
 * </ol>
 *
 * <p>Requirement 14: Real-Time Notifications.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    /** Redis channel prefix for user-specific notification events. */
    private static final String CHANNEL_PREFIX = "events:";

    private final NotificationRepository notificationRepository;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public NotificationService(NotificationRepository notificationRepository,
                               StringRedisTemplate redisTemplate,
                               ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Creates a notification for the given user and publishes it to Redis for real-time delivery.
     *
     * @param userId      the ID of the user to notify
     * @param subjectType the type of subject (e.g., "pull_request", "issue")
     * @param subjectId   the ID of the subject entity
     * @param reason      the reason for the notification (e.g., "review_approve")
     * @return the persisted {@link Notification}
     */
    @Transactional
    public Notification createNotification(Long userId, String subjectType,
                                           Long subjectId, String reason) {
        // Persist the notification record
        Notification notification = Notification.builder()
                .userId(userId)
                .subjectType(subjectType)
                .subjectId(subjectId)
                .reason(reason)
                .read(false)
                .build();
        notification = notificationRepository.save(notification);

        // Publish to Redis for fan-out to WebSocket sessions
        String channel = CHANNEL_PREFIX + userId;
        try {
            String notificationJson = objectMapper.writeValueAsString(notification);
            redisTemplate.convertAndSend(channel, notificationJson);
            log.debug("Published notification id={} to Redis channel '{}'",
                    notification.getId(), channel);
        } catch (JsonProcessingException e) {
            // Log but don't fail — the DB record is already saved
            log.warn("Failed to serialize notification id={} for Redis publish: {}",
                    notification.getId(), e.getMessage());
        }

        return notification;
    }
}
