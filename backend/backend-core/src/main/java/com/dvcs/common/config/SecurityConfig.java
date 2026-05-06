package com.dvcs.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * Core Spring Security configuration.
 *
 * <p>Declares shared security beans used across the application.
 * The full HTTP security filter chain (JWT filter, rate-limit filter, etc.)
 * will be configured in a later task.
 */
@Configuration
public class SecurityConfig {

    /**
     * BCrypt password encoder used for hashing and verifying user passwords.
     *
     * <p>Strength factor defaults to 10, which provides a good balance between
     * security and performance on modern hardware.
     *
     * @return a {@link BCryptPasswordEncoder} instance
     */
    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
