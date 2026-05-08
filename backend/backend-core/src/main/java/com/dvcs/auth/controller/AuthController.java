package com.dvcs.auth.controller;

import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.AuthResponse;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for authentication endpoints.
 *
 * <p>Handles user registration, login, and refresh-token rotation.
 * The refresh token is stored in an HttpOnly cookie to prevent JavaScript access.
 *
 * <p>Cookie settings:
 * <ul>
 *   <li>HttpOnly — not accessible via JavaScript</li>
 *   <li>Secure — transmitted over HTTPS only</li>
 *   <li>Path=/api/auth — scoped to auth endpoints only</li>
 *   <li>SameSite=Strict — CSRF protection</li>
 *   <li>MaxAge=2592000 — 30 days in seconds</li>
 * </ul>
 */
@Tag(name = "Authentication", description = "User registration, login, and token refresh")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    /** Refresh token cookie name. */
    static final String REFRESH_TOKEN_COOKIE = "refreshToken";

    /** Cookie max-age: 30 days in seconds. */
    static final int REFRESH_TOKEN_MAX_AGE = 2_592_000;

    /** Cookie path scoped to auth endpoints. */
    static final String COOKIE_PATH = "/api/auth";

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/register
    // -------------------------------------------------------------------------

    /**
     * Registers a new user account.
     *
     * @param request the registration payload (username, email, password)
     * @return HTTP 201 Created with no body on success
     */
    @Operation(summary = "Register a new user account")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "User registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "409", description = "Username or email already in use")
    })
    @PostMapping("/register")
    public ResponseEntity<Void> register(@Valid @RequestBody RegisterRequest request) {
        authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/login
    // -------------------------------------------------------------------------

    /**
     * Authenticates a user and issues a JWT access token plus a refresh token cookie.
     *
     * <p>The refresh token is set as an HttpOnly cookie; the access token is returned
     * in the response body.
     *
     * @param request  the login payload (username, password)
     * @param response the HTTP servlet response used to set the cookie
     * @return HTTP 200 OK with an {@link AuthResponse} containing the access token
     */
    @Operation(summary = "Authenticate user and issue JWT access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Login successful, access token returned"),
        @ApiResponse(responseCode = "400", description = "Invalid request payload"),
        @ApiResponse(responseCode = "401", description = "Invalid credentials")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletResponse response) {

        AuthResponse authResponse = authService.login(request);
        setRefreshTokenCookie(response, authResponse.refreshToken(), REFRESH_TOKEN_MAX_AGE);

        // Return only the access token in the body; refresh token is in the cookie
        AuthResponse bodyResponse = new AuthResponse(authResponse.accessToken(), null, authResponse.user());
        return ResponseEntity.ok(bodyResponse);
    }

    // -------------------------------------------------------------------------
    // POST /api/auth/refresh
    // -------------------------------------------------------------------------

    /**
     * Rotates the refresh token and issues a new access token.
     *
     * <p>Reads the current refresh token from the HttpOnly cookie, validates it,
     * revokes it, and issues a new access token + new refresh token cookie.
     *
     * @param refreshToken the refresh token read from the HttpOnly cookie (may be null if absent)
     * @param response     the HTTP servlet response used to set the rotated cookie
     * @return HTTP 200 OK with a new {@link AuthResponse}, or HTTP 401 if the cookie is missing
     */
    @Operation(summary = "Rotate refresh token and issue new access token")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Token refreshed successfully"),
        @ApiResponse(responseCode = "401", description = "Refresh token missing or invalid")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @CookieValue(name = REFRESH_TOKEN_COOKIE, required = false) String refreshToken,
            HttpServletResponse response) {

        if (refreshToken == null || refreshToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        AuthResponse authResponse = authService.refresh(refreshToken);
        setRefreshTokenCookie(response, authResponse.refreshToken(), REFRESH_TOKEN_MAX_AGE);

        // Return only the access token in the body; refresh token is in the cookie
        AuthResponse bodyResponse = new AuthResponse(authResponse.accessToken(), null, authResponse.user());
        return ResponseEntity.ok(bodyResponse);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Adds a {@code Set-Cookie} header for the refresh token with the required security attributes.
     *
     * <p>Spring's {@link Cookie} API does not support the {@code SameSite} attribute directly,
     * so the header is written manually to ensure all security attributes are present.
     *
     * @param response   the HTTP servlet response
     * @param tokenValue the raw refresh token value to store in the cookie
     * @param maxAge     the cookie max-age in seconds (use 0 to clear the cookie)
     */
    private void setRefreshTokenCookie(HttpServletResponse response, String tokenValue, int maxAge) {
        String cookieHeader = String.format(
                "%s=%s; HttpOnly; Secure; Path=%s; SameSite=Strict; Max-Age=%d",
                REFRESH_TOKEN_COOKIE,
                tokenValue,
                COOKIE_PATH,
                maxAge
        );
        response.addHeader("Set-Cookie", cookieHeader);
    }
}
