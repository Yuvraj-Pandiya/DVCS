package com.dvcs.common.security;

import com.dvcs.auth.domain.PersonalToken;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.repository.PersonalTokenRepository;
import com.dvcs.auth.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

/**
 * Servlet filter that authenticates incoming HTTP requests using a Personal Access Token (PAT).
 *
 * <p>This filter runs once per request (extending {@link OncePerRequestFilter}) and acts as a
 * <em>fallback</em> after {@link JwtAuthenticationFilter} in the security filter chain. It only
 * attempts PAT authentication when the {@link SecurityContextHolder} does not already hold an
 * authenticated principal (i.e. the JWT filter did not authenticate the request).
 *
 * <h2>Processing flow</h2>
 * <ol>
 *   <li>Read the {@code Authorization} header. If absent or not prefixed with {@code "Bearer "},
 *       the request is passed through unauthenticated.</li>
 *   <li>Skip processing if the {@link SecurityContextHolder} already contains an authenticated
 *       principal — the JWT filter has already handled this request.</li>
 *   <li>Compute the SHA-256 hex digest of the raw token value.</li>
 *   <li>Look up the {@link PersonalToken} record by hash via {@link PersonalTokenRepository}.
 *       If not found, continue the filter chain unauthenticated.</li>
 *   <li>If the token has an {@code expiresAt} that is in the past, respond with HTTP 401 and
 *       stop the filter chain.</li>
 *   <li>Load the associated {@link User} via {@link UserRepository}. If the user no longer
 *       exists, continue the filter chain unauthenticated.</li>
 *   <li>Build a {@link UsernamePasswordAuthenticationToken} with the {@link User} as the
 *       principal, {@code null} credentials, and authorities derived from the token's scopes
 *       (each scope is prefixed with {@code "SCOPE_"}, e.g. {@code "SCOPE_repo:read"}).</li>
 *   <li>Store the token's scopes as the request attribute {@code "pat.scopes"} so that
 *       downstream components (e.g. {@code RepoAccessGuard}) can enforce scope restrictions.</li>
 *   <li>Publish the authentication to the {@link SecurityContextHolder}.</li>
 *   <li>Continue the filter chain.</li>
 * </ol>
 *
 * <h2>Error handling</h2>
 * <p>The only case where this filter terminates the request early (without calling
 * {@code filterChain.doFilter}) is when a token is found but has expired — in that case a
 * {@code 401 Unauthorized} response is written immediately. All other failure paths (missing
 * header, unknown token, missing user) silently pass the request through unauthenticated.
 *
 * <h2>Security note</h2>
 * <p>The raw token value is <em>never</em> written to any log. Only the username extracted
 * from a successfully validated token is logged, and only at {@code DEBUG} level.
 */
@Component
public class PersonalTokenFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PersonalTokenFilter.class);

    /** HTTP header that carries the Bearer token. */
    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** Expected prefix for Bearer tokens in the Authorization header. */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * Request attribute name under which the token's scopes list is stored for downstream use.
     * Downstream components such as {@code RepoAccessGuard} read this attribute to enforce
     * scope-based access control.
     */
    public static final String PAT_SCOPES_ATTRIBUTE = "pat.scopes";

    /** Prefix applied to each scope string when building Spring Security authorities. */
    private static final String SCOPE_PREFIX = "SCOPE_";

    private final PersonalTokenRepository personalTokenRepository;
    private final UserRepository userRepository;

    /**
     * Constructs the filter with its required collaborators.
     *
     * @param personalTokenRepository used to look up tokens by SHA-256 hash
     * @param userRepository          used to load the {@link User} entity by ID
     */
    public PersonalTokenFilter(PersonalTokenRepository personalTokenRepository,
                                UserRepository userRepository) {
        this.personalTokenRepository = personalTokenRepository;
        this.userRepository = userRepository;
    }

    /**
     * Core filter logic: validates the personal access token and, on success, sets the
     * authenticated principal in the {@link SecurityContextHolder}.
     *
     * <p>This filter is a fallback — it only runs when the JWT filter has not already
     * authenticated the request. All failure paths except an expired token call
     * {@code filterChain.doFilter} and return without setting any authentication.
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

        // Step 2: Skip if the JWT filter already authenticated this request.
        if (SecurityContextHolder.getContext().getAuthentication() != null
                && SecurityContextHolder.getContext().getAuthentication().isAuthenticated()) {
            filterChain.doFilter(request, response);
            return;
        }

        // Step 3: Strip the "Bearer " prefix to get the raw token value.
        String rawToken = authHeader.substring(BEARER_PREFIX.length());

        // Step 4: Compute the SHA-256 hex digest of the raw token.
        String tokenHash = sha256Hex(rawToken);

        // Step 5: Look up the personal token record by hash.
        PersonalToken personalToken = personalTokenRepository.findByTokenHash(tokenHash)
                .orElse(null);
        if (personalToken == null) {
            log.debug("No personal access token found for request to {}", request.getRequestURI());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 6: Reject expired tokens with 401.
        if (personalToken.getExpiresAt() != null
                && Instant.now().isAfter(personalToken.getExpiresAt())) {
            log.debug("Personal access token has expired for request to {}", request.getRequestURI());
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.getWriter().write("Personal access token has expired.");
            return;
        }

        // Step 7: Load the associated user entity.
        User user = userRepository.findById(personalToken.getUserId()).orElse(null);
        if (user == null) {
            log.debug("Personal access token references unknown user id={}", personalToken.getUserId());
            filterChain.doFilter(request, response);
            return;
        }

        // Step 8: Build authorities from the token's declared scopes.
        List<SimpleGrantedAuthority> authorities = personalToken.getScopes().stream()
                .map(scope -> new SimpleGrantedAuthority(SCOPE_PREFIX + scope))
                .toList();

        // Step 9: Build the Spring Security authentication token.
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, authorities);

        // Step 10: Attach request-level details (remote address, session id, etc.).
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        // Step 11: Store scopes as a request attribute for downstream scope enforcement.
        request.setAttribute(PAT_SCOPES_ATTRIBUTE, personalToken.getScopes());

        // Step 12: Publish the authentication to the SecurityContext.
        SecurityContextHolder.getContext().setAuthentication(authentication);
        log.debug("Authenticated request via PAT for user '{}' to {}",
                user.getUsername(), request.getRequestURI());

        // Step 13: Continue the filter chain.
        filterChain.doFilter(request, response);
    }

    /**
     * Computes the SHA-256 hex digest of the given string (UTF-8 encoded).
     *
     * @param input the string to hash
     * @return lowercase hex-encoded SHA-256 digest (64 characters)
     */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hashBytes);
        } catch (NoSuchAlgorithmException e) {
            // SHA-256 is guaranteed to be available in every JVM (JCA spec)
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }
}
