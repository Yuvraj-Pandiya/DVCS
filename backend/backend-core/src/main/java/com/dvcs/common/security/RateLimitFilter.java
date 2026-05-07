package com.dvcs.common.security;

import com.dvcs.auth.service.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.ConsumptionProbe;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Rate-limiting filter using Bucket4j with Redis-backed distributed buckets.
 *
 * <p>Four rate-limit tiers are enforced (Req 17):
 * <ol>
 *   <li><strong>Auth endpoints</strong> ({@code /api/auth/**}): 10 req/min keyed by IP</li>
 *   <li><strong>Git push endpoints</strong> ({@code POST /api/git/**}): 100 req/hour keyed by userId</li>
 *   <li><strong>Authenticated API</strong>: 5,000 req/hour keyed by userId</li>
 *   <li><strong>Unauthenticated reads</strong>: 60 req/min keyed by IP</li>
 * </ol>
 *
 * <p>Bucket state is stored in Redis via Bucket4j's {@link ProxyManager} so that
 * limits are enforced consistently across all server instances (Req 19.5).
 *
 * <p>When a limit is exceeded the filter writes HTTP 429 directly to the response
 * and sets the {@code Retry-After} header to the number of seconds until the
 * bucket refills.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(RateLimitFilter.class);

    // -------------------------------------------------------------------------
    // Bucket configuration constants
    // -------------------------------------------------------------------------

    /** Auth endpoints: 10 requests per minute per IP. */
    private static final BucketConfiguration AUTH_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(10)
                    .refillGreedy(10, Duration.ofMinutes(1))
                    .build())
            .build();

    /** Git push endpoints: 100 requests per hour per authenticated user. */
    private static final BucketConfiguration GIT_PUSH_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(100)
                    .refillGreedy(100, Duration.ofHours(1))
                    .build())
            .build();

    /** Authenticated API: 5,000 requests per hour per user. */
    private static final BucketConfiguration AUTHENTICATED_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(5000)
                    .refillGreedy(5000, Duration.ofHours(1))
                    .build())
            .build();

    /** Unauthenticated reads: 60 requests per minute per IP. */
    private static final BucketConfiguration UNAUTHENTICATED_CONFIG = BucketConfiguration.builder()
            .addLimit(Bandwidth.builder()
                    .capacity(60)
                    .refillGreedy(60, Duration.ofMinutes(1))
                    .build())
            .build();

    // -------------------------------------------------------------------------
    // Redis key prefixes
    // -------------------------------------------------------------------------

    private static final String KEY_AUTH      = "ratelimit:auth:";
    private static final String KEY_GIT_PUSH  = "ratelimit:gitpush:";
    private static final String KEY_AUTHED    = "ratelimit:authed:";
    private static final String KEY_UNAUTHED  = "ratelimit:unauthed:";

    // -------------------------------------------------------------------------
    // Dependencies
    // -------------------------------------------------------------------------

    private final ProxyManager<String> proxyManager;
    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public RateLimitFilter(ProxyManager<String> proxyManager,
                           JwtUtil jwtUtil,
                           ObjectMapper objectMapper) {
        this.proxyManager = proxyManager;
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    // -------------------------------------------------------------------------
    // Filter logic
    // -------------------------------------------------------------------------

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String path   = request.getRequestURI();
        String method = request.getMethod();
        String ip     = resolveClientIp(request);

        // Determine which bucket to use and resolve the bucket key
        String bucketKey;
        BucketConfiguration config;

        if (isAuthEndpoint(path)) {
            // Auth endpoints: keyed by IP, 10 req/min
            bucketKey = KEY_AUTH + ip;
            config    = AUTH_CONFIG;

        } else if (isGitPushEndpoint(path, method)) {
            // Git push: keyed by userId if authenticated, else fall through to unauthenticated
            String userId = resolveUserId(request);
            if (userId != null) {
                bucketKey = KEY_GIT_PUSH + userId;
                config    = GIT_PUSH_CONFIG;
            } else {
                bucketKey = KEY_UNAUTHED + ip;
                config    = UNAUTHENTICATED_CONFIG;
            }

        } else {
            // General endpoints: authenticated (per userId) or unauthenticated (per IP)
            String userId = resolveUserId(request);
            if (userId != null) {
                bucketKey = KEY_AUTHED + userId;
                config    = AUTHENTICATED_CONFIG;
            } else {
                bucketKey = KEY_UNAUTHED + ip;
                config    = UNAUTHENTICATED_CONFIG;
            }
        }

        // Resolve (or create) the bucket from Redis
        Bucket bucket = resolveBucket(bucketKey, config);

        // Try to consume one token
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        if (probe.isConsumed()) {
            filterChain.doFilter(request, response);
        } else {
            long retryAfterSeconds = probe.getNanosToWaitForRefill() / 1_000_000_000L;
            writeTooManyRequests(response, retryAfterSeconds);
        }
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Resolves or creates a Bucket4j bucket backed by Redis for the given key.
     *
     * @param key    the Redis key for this bucket
     * @param config the bucket configuration (capacity + refill rate)
     * @return the resolved or newly created {@link Bucket}
     */
    private Bucket resolveBucket(String key, BucketConfiguration config) {
        Supplier<BucketConfiguration> configSupplier = () -> config;
        return proxyManager.builder().build(key, configSupplier);
    }

    /**
     * Returns {@code true} if the request path targets an auth endpoint.
     */
    private static boolean isAuthEndpoint(String path) {
        return path != null && path.startsWith("/api/auth/");
    }

    /**
     * Returns {@code true} if the request is a Git push (POST to /api/git/**).
     */
    private static boolean isGitPushEndpoint(String path, String method) {
        return "POST".equalsIgnoreCase(method)
                && path != null
                && path.startsWith("/api/git/")
                && path.endsWith("/git-receive-pack");
    }

    /**
     * Resolves the authenticated user ID from the current {@link SecurityContextHolder}
     * or from the {@code Authorization: Bearer} header directly.
     *
     * <p>This filter runs before the JWT filter in the chain, so the security context
     * may not yet be populated. We attempt a lightweight token extraction here.
     *
     * @param request the current HTTP request
     * @return the user ID as a string, or {@code null} if unauthenticated
     */
    private String resolveUserId(HttpServletRequest request) {
        // First try the security context (populated by earlier filter invocations or tests)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated() && !"anonymousUser".equals(auth.getPrincipal())) {
            return auth.getName();
        }

        // Fall back to extracting from the Bearer token directly
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                if (jwtUtil.validateToken(token)) {
                    String username = jwtUtil.extractUsername(token);
                    if (username != null) {
                        return username;
                    }
                }
            } catch (Exception e) {
                // Invalid token — treat as unauthenticated for rate-limiting purposes
                log.trace("Could not extract user from token for rate limiting: {}", e.getMessage());
            }
        }

        return null;
    }

    /**
     * Resolves the client IP address, respecting the {@code X-Forwarded-For} header
     * set by the nginx reverse proxy.
     *
     * @param request the current HTTP request
     * @return the client IP address string
     */
    private static String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            // X-Forwarded-For may contain a comma-separated list; take the first (original client)
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    /**
     * Writes an HTTP 429 response with a JSON error envelope and {@code Retry-After} header.
     *
     * @param response          the HTTP servlet response
     * @param retryAfterSeconds seconds until the rate-limit window resets
     */
    private void writeTooManyRequests(HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "error", "RATE_LIMIT_EXCEEDED",
                "message", "Too many requests. Please retry after " + retryAfterSeconds + " seconds.",
                "details", Map.of("retryAfterSeconds", retryAfterSeconds),
                "timestamp", java.time.Instant.now().toString()
        );
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
