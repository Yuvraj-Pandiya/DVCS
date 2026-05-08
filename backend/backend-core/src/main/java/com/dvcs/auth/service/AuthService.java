package com.dvcs.auth.service;

import com.dvcs.auth.domain.RefreshToken;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.AuthResponse;
import com.dvcs.auth.dto.UserResponse;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.exception.ConflictException;
import com.dvcs.auth.exception.UnauthorizedException;
import com.dvcs.auth.repository.RefreshTokenRepository;
import com.dvcs.auth.repository.UserRepository;
import com.dvcs.common.audit.Audited;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.UUID;

/**
 * Core authentication service responsible for user registration, login, and refresh-token rotation.
 *
 * <p>Security invariants:
 * <ul>
 *   <li>Passwords are stored as bcrypt hashes; the raw password is never persisted or logged.</li>
 *   <li>Refresh tokens are stored as SHA-256 hashes; the raw UUID is returned to the caller once
 *       and never stored.</li>
 *   <li>Login errors do not reveal whether the username or the password was incorrect.</li>
 *   <li>JWT secrets and raw token values are never logged.</li>
 * </ul>
 */
@Service
@Transactional
public class AuthService {

    /** Refresh tokens are valid for 30 days. */
    private static final long REFRESH_TOKEN_EXPIRY_DAYS = 30L;

    private final UserRepository userRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final BCryptPasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository,
                       RefreshTokenRepository refreshTokenRepository,
                       BCryptPasswordEncoder passwordEncoder,
                       JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.refreshTokenRepository = refreshTokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    // -------------------------------------------------------------------------
    // Public API
    // -------------------------------------------------------------------------

    /**
     * Registers a new user.
     *
     * <p>Checks for duplicate username and email, hashes the password with bcrypt,
     * persists the new {@link User}, and returns the saved entity.
     *
     * @param req the registration request (username, email, password)
     * @return the newly created and persisted {@link User}
     * @throws ConflictException if the username or email is already taken
     */
    public User register(RegisterRequest req) {
        if (userRepository.findByUsername(req.username()).isPresent()) {
            throw new ConflictException("Username '" + req.username() + "' is already taken.");
        }
        if (userRepository.findByEmail(req.email()).isPresent()) {
            throw new ConflictException("Email '" + req.email() + "' is already registered.");
        }

        String passwordHash = passwordEncoder.encode(req.password());

        User user = User.builder()
                .username(req.username())
                .email(req.email())
                .passwordHash(passwordHash)
                .build();

        return userRepository.save(user);
    }

    /**
     * Authenticates a user and issues a JWT access token plus a refresh token.
     *
     * <p>The error message is intentionally generic to avoid revealing whether the
     * username or the password was incorrect (requirement 1.4).
     *
     * @param req the login request (username, password)
     * @return an {@link AuthResponse} containing the access token and the raw refresh token
     * @throws UnauthorizedException if the credentials are invalid
     */
    @Audited(action = "login", resourceType = "user")
    public AuthResponse login(LoginRequest req) {
        User user = userRepository.findByUsername(req.username())
                .orElseThrow(() -> new UnauthorizedException("Invalid credentials."));

        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid credentials.");
        }

        String accessToken = jwtUtil.generateAccessToken(user);
        String rawRefreshToken = generateAndPersistRefreshToken(user.getId());

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl()
        );

        return new AuthResponse(accessToken, rawRefreshToken, userResponse);
    }

    /**
     * Rotates a refresh token and returns a new access token and a new refresh token.
     *
     * <p>The incoming raw token is hashed with SHA-256 and looked up in the database.
     * If found, valid, and not revoked, the old token is revoked and a new pair is issued.
     *
     * @param rawRefreshToken the raw (unhashed) refresh token previously issued to the client
     * @return an {@link AuthResponse} containing the new access token and the new raw refresh token
     * @throws UnauthorizedException if the token is not found, revoked, or expired
     */
    public AuthResponse refresh(String rawRefreshToken) {
        String tokenHash = sha256Hex(rawRefreshToken);

        RefreshToken stored = refreshTokenRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));

        if (stored.isRevoked()) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }
        if (Instant.now().isAfter(stored.getExpiresAt())) {
            throw new UnauthorizedException("Invalid or expired refresh token.");
        }

        // Revoke the consumed token (rotation — one-time use)
        stored.setRevoked(true);
        refreshTokenRepository.save(stored);

        User user = userRepository.findById(stored.getUserId())
                .orElseThrow(() -> new UnauthorizedException("Invalid or expired refresh token."));

        String newAccessToken = jwtUtil.generateAccessToken(user);
        String newRawRefreshToken = generateAndPersistRefreshToken(user.getId());

        UserResponse userResponse = new UserResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getAvatarUrl()
        );

        return new AuthResponse(newAccessToken, newRawRefreshToken, userResponse);
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Generates a random UUID refresh token, hashes it with SHA-256, persists the hash,
     * and returns the raw UUID string to the caller.
     *
     * @param userId the ID of the user the token belongs to
     * @return the raw (unhashed) refresh token UUID string
     */
    private String generateAndPersistRefreshToken(Long userId) {
        String rawToken = UUID.randomUUID().toString();
        String tokenHash = sha256Hex(rawToken);

        RefreshToken refreshToken = RefreshToken.builder()
                .userId(userId)
                .tokenHash(tokenHash)
                .expiresAt(Instant.now().plus(REFRESH_TOKEN_EXPIRY_DAYS, ChronoUnit.DAYS))
                .revoked(false)
                .build();

        refreshTokenRepository.save(refreshToken);
        return rawToken;
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
