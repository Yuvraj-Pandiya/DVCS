package com.dvcs.common.security;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.auth.service.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Servlet filter that authenticates incoming HTTP requests using a JWT Bearer token.
 *
 * <p>This filter runs once per request (extending {@link OncePerRequestFilter}) and is
 * responsible for extracting the JWT from the {@code Authorization} header, validating it,
 * and populating the {@link SecurityContextHolder} with an authenticated principal so that
 * downstream Spring Security components (e.g. method-level {@code @PreAuthorize} checks or
 * the HTTP security filter chain) can make access-control decisions.
 *
 * <h2>Processing flow</h2>
 * <ol>
 *   <li>Read the {@code Authorization} header. If absent or not prefixed with {@code "Bearer "},
 *       the request is passed through unauthenticated — the downstream security configuration
 *       will reject it with 401/403 if the endpoint requires authentication.</li>
 *   <li>Extract the raw token string (everything after {@code "Bearer "}).</li>
 *   <li>Delegate signature and expiry validation to {@link JwtUtil#validateToken(String)}.
 *       An invalid or expired token causes the request to proceed unauthenticated.</li>
 *   <li>Extract the user ID and username from the token claims.</li>
 *   <li>Load the {@link User} entity from the database. If the user no longer exists
 *       (e.g. account deleted after the token was issued), the request proceeds
 *       unauthenticated.</li>
 *   <li>Build a {@link UsernamePasswordAuthenticationToken} with the {@link User} entity as
 *       the principal, {@code null} credentials, and a single {@code ROLE_USER} authority.</li>
 *   <li>Attach request-level details via {@link WebAuthenticationDetailsSource} so that
 *       audit and session-management components have access to the remote address.</li>
 *   <li>Store the authentication object in the {@link SecurityContextHolder} for the
 *       duration of the request.</li>
 *   <li>Continue the filter chain.</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 * <p>This filter intentionally swallows all authentication failures silently. It never
 * throws an exception to the caller — any failure (missing header, invalid token, unknown
 * user) simply results in the request proceeding without an authenticated principal.
 * Downstream security rules are responsible for returning 401 or 403 responses.
 *
 * <h2>Security note</h2>
 * <p>The raw token value is <em>never</em> written to any log. Only the username extracted
 * from a successfully validated token is logged, and only at {@code DEBUG} level.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    /** HTTP header that carries the Bearer token. */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Expected prefix for JWT Bearer tokens in the Authorization header. */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    /**
     * Constructs the filter with its required collaborators.
     *
     * @param jwtUtil        used to validate tokens and extract claims
     * @param userRepository used to load the {@link User} entity by ID
     */
    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    /**
     * Core filter logic: validates the JWT and, on success, sets the authenticated
     * principal in the {@link SecurityContextHolder}.
     *
     * <p>All failure paths (missing/malformed header, invalid token, unknown user) call
     * {@code filterChain.doFilter} and return without setting any authentication, leaving
     * the request unauthenticated for downstream processing.
     *
     * @param request     the incoming HTTP request
     * @param response    the HTTP response
     * @param filterChain the remaining filter chain
     * @throws ServletException if a downstream filter throws a {@link ServletException}
     * @throws IOException      if a downstream filter throws an {@link IOException}
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        // Step 1: Extract the Authorization header.
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 2: Strip the "Bearer " prefix to get the raw token.
        String token = authHeader.substring(BEARER_PREFIX.length());

        // Step 3: Validate the token's signature and expiry.
        if (!jwtUtil.validateToken(token)) {
            log.debug("JWT validation failed for request to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 4: Extract claims from the validated token.
        Long userId = jwtUtil.extractUserId(token);
        String username = jwtUtil.extractUsername(token);

        // Step 5: Load the user entity from the database.
        User user = userRepository.findById(userId).orElse(null);
        if (user == null) {
            log.debug("Authenticated token references unknown user id={}", userId);
            filterChain.doFilter(request, response);
            return;
        }

        // Step 6: Build the Spring Security authentication token.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(
                        user,
                        null,
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));

        // Step 7: Attach request-level details (remote address, session id, etc.).
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Step 8: Publish the authentication to the SecurityContext.
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated request for user '{}' to {}", username, request.getRequestURI());

        // Step 9: Continue the filter chain.
        filterChain.doFilter(request, response);
    }
}
