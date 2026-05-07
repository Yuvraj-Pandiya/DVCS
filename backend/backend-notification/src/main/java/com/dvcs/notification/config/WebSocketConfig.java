package com.dvcs.notification.config;

import com.dvcs.auth.service.JwtUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.List;

/**
 * WebSocket/STOMP configuration for the real-time notification system.
 *
 * <p>Registers the STOMP endpoint {@code /ws/notifications} with SockJS fallback,
 * configures the in-memory message broker with {@code /topic} and {@code /queue}
 * destination prefixes, and adds a {@link ChannelInterceptor} that authenticates
 * STOMP CONNECT frames via JWT.
 *
 * <p>STOMP topology:
 * <ul>
 *   <li>{@code /queue/notifications} — user-specific queue for notification messages</li>
 *   <li>{@code /topic/notifications/{userId}} — user-specific topic (alternative)</li>
 *   <li>{@code /app} — application destination prefix for client-to-server messages</li>
 * </ul>
 *
 * <p>Requirement 14: Real-Time Notifications.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final Logger log = LoggerFactory.getLogger(WebSocketConfig.class);

    private final JwtUtil jwtUtil;

    public WebSocketConfig(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    /**
     * Registers the STOMP endpoint {@code /ws/notifications} with SockJS fallback.
     * Clients connect to this endpoint to establish a WebSocket (or SockJS) session.
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws/notifications")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }

    /**
     * Configures the in-memory message broker.
     *
     * <ul>
     *   <li>Broker destinations: {@code /topic} and {@code /queue}</li>
     *   <li>Application destination prefix: {@code /app} (for client-to-server messages)</li>
     *   <li>User destination prefix: {@code /user} (for {@code convertAndSendToUser})</li>
     * </ul>
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    /**
     * Adds a {@link ChannelInterceptor} to the inbound channel that authenticates
     * STOMP CONNECT frames via JWT.
     *
     * <p>The interceptor extracts the {@code Authorization} header from the STOMP
     * CONNECT frame, validates the JWT using {@link JwtUtil}, and sets the
     * {@link SecurityContextHolder} authentication so that
     * {@code SimpMessagingTemplate.convertAndSendToUser} can resolve the user.
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor =
                        MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);

                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader != null && authHeader.startsWith("Bearer ")) {
                        String token = authHeader.substring("Bearer ".length());
                        if (jwtUtil.validateToken(token)) {
                            Long userId = jwtUtil.extractUserId(token);
                            String username = jwtUtil.extractUsername(token);

                            UsernamePasswordAuthenticationToken auth =
                                    new UsernamePasswordAuthenticationToken(
                                            username,
                                            null,
                                            List.of(new SimpleGrantedAuthority("ROLE_USER")));

                            // Set the user on the STOMP session so convertAndSendToUser works
                            accessor.setUser(auth);

                            // Also set in SecurityContextHolder for this thread
                            SecurityContextHolder.getContext().setAuthentication(auth);

                            log.debug("Authenticated WebSocket CONNECT for user '{}' (id={})",
                                    username, userId);
                        } else {
                            log.debug("Invalid JWT in WebSocket CONNECT frame");
                        }
                    }
                }
                return message;
            }
        });
    }
}
