package com.dvcs.auth.service;

import com.dvcs.auth.domain.User;
import org.springframework.stereotype.Component;

/**
 * Placeholder implementation of {@link JwtUtil}.
 *
 * <p><strong>This class is a stub.</strong> All methods throw {@link UnsupportedOperationException}.
 * The real implementation will be provided in task 3.4 using the JJWT library.
 *
 * <p>It is registered as a Spring bean so that {@link AuthService} can be wired up
 * and the application context can start without the full JWT implementation.
 */
@Component
public class JwtUtilImpl implements JwtUtil {

    @Override
    public String generateAccessToken(User user) {
        throw new UnsupportedOperationException(
                "JwtUtilImpl is a stub — real implementation is provided in task 3.4");
    }

    @Override
    public boolean validateToken(String token) {
        throw new UnsupportedOperationException(
                "JwtUtilImpl is a stub — real implementation is provided in task 3.4");
    }

    @Override
    public Long extractUserId(String token) {
        throw new UnsupportedOperationException(
                "JwtUtilImpl is a stub — real implementation is provided in task 3.4");
    }

    @Override
    public String extractUsername(String token) {
        throw new UnsupportedOperationException(
                "JwtUtilImpl is a stub — real implementation is provided in task 3.4");
    }
}
