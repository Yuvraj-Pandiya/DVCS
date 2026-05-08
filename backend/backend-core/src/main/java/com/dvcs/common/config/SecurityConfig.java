package com.dvcs.common.config;

import com.dvcs.common.security.JwtAuthenticationFilter;
import com.dvcs.common.security.PersonalTokenFilter;
import com.dvcs.common.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Spring Security configuration for the DVCS platform.
 *
 * <p>Defines the HTTP security filter chain with the following ordering:
 * <ol>
 *   <li>{@link RateLimitFilter} — per-IP / per-user rate limiting (stub; full impl in task 16.1)</li>
 *   <li>{@link JwtAuthenticationFilter} — validates Bearer JWT tokens</li>
 *   <li>{@link PersonalTokenFilter} — fallback PAT authentication</li>
 *   <li>Spring Security's built-in filter chain (authorization, etc.)</li>
 * </ol>
 *
 * <h2>Access rules</h2>
 * <ul>
 *   <li>{@code /api/auth/**} — public (no authentication required)</li>
 *   <li>GET {@code /api/repos/**}, {@code /api/git/**}, {@code /api/search/**} — public reads;
 *       fine-grained access control is enforced by {@code @PreAuthorize} on controllers</li>
 *   <li>All other requests — require authentication</li>
 * </ul>
 *
 * <h2>CORS</h2>
 * <p>Allowed origins are read from the {@code CORS_ALLOWED_ORIGINS} environment variable
 * (comma-separated list). Falls back to {@code http://localhost:3000} when the variable is
 * not set (development convenience only — set the variable in production).
 *
 * <h2>Session management</h2>
 * <p>Stateless — no HTTP session is created or used. All state is carried in the JWT.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    /**
     * Comma-separated list of allowed CORS origins, read from the {@code CORS_ALLOWED_ORIGINS}
     * environment variable. Defaults to {@code http://localhost:3000} for local development.
     */
    @Value("${cors.allowed-origins:#{environment['CORS_ALLOWED_ORIGINS'] ?: 'http://localhost:3000'}}")
    private String corsAllowedOrigins;

    private final RateLimitFilter rateLimitFilter;
    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final PersonalTokenFilter personalTokenFilter;

    public SecurityConfig(RateLimitFilter rateLimitFilter,
                          JwtAuthenticationFilter jwtAuthenticationFilter,
                          PersonalTokenFilter personalTokenFilter) {
        this.rateLimitFilter = rateLimitFilter;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.personalTokenFilter = personalTokenFilter;
    }

    // -------------------------------------------------------------------------
    // Security filter chain
    // -------------------------------------------------------------------------

    /**
     * Configures the main HTTP security filter chain.
     *
     * <p>Filter ordering:
     * <pre>
     * RateLimitFilter → JwtAuthenticationFilter → PersonalTokenFilter → (Spring Security chain)
     * </pre>
     *
     * @param http the {@link HttpSecurity} builder provided by Spring Security
     * @return the configured {@link SecurityFilterChain}
     * @throws Exception if configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            // Disable CSRF — stateless REST API uses JWT/PAT, not cookies
            .csrf(csrf -> csrf.disable())

            // CORS — configured from CORS_ALLOWED_ORIGINS env var
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))

            // Session management — stateless; no HttpSession is created
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

            // Authorization rules
            .authorizeHttpRequests(auth -> auth
                // Auth endpoints are fully public
                .requestMatchers("/api/auth/**").permitAll()
                // OpenAPI / Swagger UI — publicly accessible for developer convenience
                .requestMatchers("/api/docs/**", "/api/docs.yaml", "/api/swagger-ui/**",
                        "/api/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**").permitAll()
                // Actuator endpoints — allowed for health checks
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // Public read access for repos, git, search, and user profiles
                .requestMatchers(HttpMethod.GET, "/api/repos/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/git/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/search/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/users/**").permitAll()
                // Everything else requires authentication
                .anyRequest().authenticated()
            )

            // Exception handling — return JSON error envelopes
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    response.setStatus(HttpStatus.UNAUTHORIZED.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        new ObjectMapper().writeValueAsString(
                            Map.of("error", "Unauthorized", "message", authException.getMessage())
                        )
                    );
                })
                .accessDeniedHandler((request, response, accessDeniedException) -> {
                    response.setStatus(HttpStatus.FORBIDDEN.value());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    response.getWriter().write(
                        new ObjectMapper().writeValueAsString(
                            Map.of("error", "Forbidden", "message", accessDeniedException.getMessage())
                        )
                    );
                })
            )

            // Filter chain ordering:
            //   RateLimitFilter → JwtAuthenticationFilter → PersonalTokenFilter
            //
            // addFilterBefore(A, B) inserts A immediately before B in the chain.
            // addFilterAfter(A, B)  inserts A immediately after  B in the chain.
            .addFilterBefore(rateLimitFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
            .addFilterAfter(personalTokenFilter, JwtAuthenticationFilter.class);

        return http.build();
    }

    // -------------------------------------------------------------------------
    // CORS configuration
    // -------------------------------------------------------------------------

    /**
     * Builds a {@link CorsConfigurationSource} from the {@code CORS_ALLOWED_ORIGINS}
     * environment variable (comma-separated list of origins).
     *
     * <p>All HTTP methods and headers are allowed. Credentials (cookies, Authorization
     * headers) are permitted so that the browser can send JWT tokens cross-origin.
     *
     * @return the configured {@link CorsConfigurationSource}
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        // Prefer the environment variable directly; fall back to the injected value.
        String originsEnv = System.getenv("CORS_ALLOWED_ORIGINS");
        String originsValue = (originsEnv != null && !originsEnv.isBlank())
                ? originsEnv
                : corsAllowedOrigins;

        List<String> allowedOrigins = Arrays.stream(originsValue.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .toList();

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(allowedOrigins);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS", "HEAD"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization", "Content-Type"));
        config.setAllowCredentials(true);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    // -------------------------------------------------------------------------
    // Shared security beans
    // -------------------------------------------------------------------------

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

    /**
     * Exposes the {@link AuthenticationManager} as a Spring bean so that
     * {@code AuthService} (and any other service that needs to programmatically
     * authenticate a user) can inject it.
     *
     * @param authConfig the auto-configured {@link AuthenticationConfiguration}
     * @return the application's {@link AuthenticationManager}
     * @throws Exception if the manager cannot be obtained
     */
    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration authConfig)
            throws Exception {
        return authConfig.getAuthenticationManager();
    }
}
