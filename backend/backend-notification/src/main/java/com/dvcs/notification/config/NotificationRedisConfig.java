package com.dvcs.notification.config;

import com.dvcs.notification.service.NotificationFanout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * Redis configuration for the notification fan-out system.
 *
 * <p>Registers a {@link RedisMessageListenerContainer} that subscribes to the
 * Redis pattern {@code events:*} and routes messages to {@link NotificationFanout}.
 *
 * <p>Requirement 14.5: Redis pub/sub fan-out.
 */
@Configuration
public class NotificationRedisConfig {

    /**
     * Creates a {@link RedisMessageListenerContainer} that subscribes to the
     * {@code events:*} pattern and delegates messages to {@link NotificationFanout}.
     *
     * @param connectionFactory the Redis connection factory
     * @param notificationFanout the message listener that forwards to WebSocket sessions
     * @return the configured listener container
     */
    @Bean
    public RedisMessageListenerContainer notificationListenerContainer(
            RedisConnectionFactory connectionFactory,
            NotificationFanout notificationFanout) {

        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(notificationFanout, new PatternTopic("events:*"));
        return container;
    }
}
