package com.dvcs.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Stub rate-limiting filter — full implementation is in task 16.1.
 *
 * <p>This placeholder sits at the front of the security filter chain
 * (before {@link JwtAuthenticationFilter}) and currently passes every
 * request through without restriction. The real implementation will use
 * Bucket4j to enforce per-IP and per-user rate limits.
 *
 * <p>Rate-limit strategy (to be implemented in task 16.1):
 * <ul>
 *   <li>Unauthenticated reads: 60 req/min keyed by IP</li>
 *   <li>Authenticated API: 5,000 req/hour keyed by userId</li>
 *   <li>Git push: 100 req/hour keyed by userId</li>
 *   <li>Auth endpoints {@code /api/auth/**}: 10 req/min keyed by IP</li>
 * </ul>
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    /**
     * Passes the request through without rate-limiting.
     * The full Bucket4j-based implementation will be added in task 16.1.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {
        filterChain.doFilter(request, response);
    }
}
