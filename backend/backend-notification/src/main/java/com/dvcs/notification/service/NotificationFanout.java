package com.dvcs.notification.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

/**
 * Redis pub/sub listener that fans out notifications to WebSocket/STOMP sessions.
 *
 * <p>This service subscribes to the Redis pattern {@code events:*} via a
 * {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}.
 * When a message arrives on channel {@code events:{userId}}, it extracts the userId
 * from the channel name and forwards the payload to the user's STOMP queue
 * {@code /queue/notifications}.
 *
 * <p>This enables horizontal scaling: any server instance that receives the Redis
 * message will deliver it to any WebSocket sessions it is hosting for that user.
 *
 * <p>Requirement 14.5: Redis pub/sub fan-out.
 */
@Service
public class NotificationFanout implements MessageListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationFanout.class);

    /** Redis channel prefix — channels follow the pattern {@code events:{userId}}. */
    private static final String CHANNEL_PREFIX = "events:";

    /** STOMP destination for user-specific notification messages. */
    private static final String STOMP_DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;

    public NotificationFanout(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    /**
     * Called by the {@link org.springframework.data.redis.listener.RedisMessageListenerContainer}
     * when a message arrives on a channel matching {@code events:*}.
     *
     * @param message the Redis message containing the notification JSON as body
     *                and the channel name as pattern/channel
     * @param pattern the subscription pattern (may be null for exact-channel subscriptions)
     */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String channel = new String(message.getChannel());
        String payload = new String(message.getBody());

        // Extract userId from channel name: "events:{userId}"
        if (!channel.startsWith(CHANNEL_PREFIX)) {
            log.warn("Received message on unexpected channel '{}', ignoring", channel);
            return;
        }

        String userId = channel.substring(CHANNEL_PREFIX.length());
        if (userId.isBlank()) {
            log.warn("Could not extract userId from channel '{}', ignoring", channel);
            return;
        }

        log.debug("Fanout notification to user '{}' via STOMP", userId);

        // Forward to the user's STOMP queue
        messagingTemplate.convertAndSendToUser(userId, STOMP_DESTINATION, payload);
    }
}
