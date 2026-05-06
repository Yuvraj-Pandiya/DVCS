package com.dvcs.auth.service;

import com.dvcs.auth.domain.RefreshToken;
import com.dvcs.auth.domain.User;
import com.dvcs.auth.dto.AuthResponse;
import com.dvcs.auth.dto.LoginRequest;
import com.dvcs.auth.dto.RegisterRequest;
import com.dvcs.auth.exception.ConflictException;
import com.dvcs.auth.exception.UnauthorizedException;
import com.dvcs.auth.repository.RefreshTokenRepository;
import com.dvcs.auth.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AuthService}.
 *
 * <p>All collaborators (repositories, password encoder, JWT util) are mocked with Mockito
 * so that tests run without a database or Spring context.
 *
 * <p>Correctness properties verified:
 * <ul>
 *   <li>Registration persists a user with a bcrypt-hashed password and returns the saved entity.</li>
 *   <li>Duplicate username or email during registration throws {@link ConflictException}.</li>
 *   <li>Successful login returns a non-null access token and a non-null refresh token.</li>
 *   <li>Login with wrong password throws {@link UnauthorizedException} with a generic message
 *       that does not reveal which field was incorrect.</li>
 *   <li>Token refresh rotates the old token (marks it revoked) and returns a new pair.</li>
 *   <li>Refresh with a revoked or expired token throws {@link UnauthorizedException}.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    // -------------------------------------------------------------------------
    // Mocks & SUT
    // -------------------------------------------------------------------------

    @Mock
    private UserRepository userRepository;

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private BCryptPasswordEncoder passwordEncoder;

    @Mock
    private JwtUtil jwtUtil;

    @InjectMocks
    private AuthService authService;

    // -------------------------------------------------------------------------
    // Shared fixtures
    // -------------------------------------------------------------------------

    private static final String USERNAME   = "alice";
    private static final String EMAIL      = "alice@example.com";
    private static final String PASSWORD   = "s3cr3tP@ss";
    private static final String HASH       = "$2a$10$hashedpassword";
    private static final String ACCESS_JWT = "header.payload.signature";

    private User savedUser;

    @BeforeEach
    void setUp() {
        savedUser = User.builder()
                .id(1L)
                .username(USERNAME)
                .email(EMAIL)
                .passwordHash(HASH)
                .build();
    }

    // =========================================================================
    // register()
    // =========================================================================

    @Nested
    @DisplayName("register()")
    class Register {

        @Test
        @DisplayName("success — returns the saved User entity")
        void register_success_returnsSavedUser() {
            // Arrange
            RegisterRequest req = new RegisterRequest(USERNAME, EMAIL, PASSWORD);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
            when(passwordEncoder.encode(PASSWORD)).thenReturn(HASH);
            when(userRepository.save(any(User.class))).thenReturn(savedUser);

            // Act
            User result = authService.register(req);

            // Assert — returned entity is the one from the repository
            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(1L);
            assertThat(result.getUsername()).isEqualTo(USERNAME);
            assertThat(result.getEmail()).isEqualTo(EMAIL);

            // Assert — password is hashed before saving
            ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
            verify(userRepository).save(userCaptor.capture());
            assertThat(userCaptor.getValue().getPasswordHash()).isEqualTo(HASH);
            // Raw password must NOT be stored
            assertThat(userCaptor.getValue().getPasswordHash()).doesNotContain(PASSWORD);
        }

        @Test
        @DisplayName("duplicate username — throws ConflictException before touching email or saving")
        void register_duplicateUsername_throwsConflictException() {
            // Arrange
            RegisterRequest req = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(savedUser));

            // Act & Assert
            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(USERNAME);

            // Email check and save must never be reached
            verify(userRepository, never()).findByEmail(anyString());
            verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("duplicate email — throws ConflictException without saving")
        void register_duplicateEmail_throwsConflictException() {
            // Arrange
            RegisterRequest req = new RegisterRequest(USERNAME, EMAIL, PASSWORD);
            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.empty());
            when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(savedUser));

            // Act & Assert
            assertThatThrownBy(() -> authService.register(req))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining(EMAIL);

            verify(userRepository, never()).save(any());
        }
    }

    // =========================================================================
    // login()
    // =========================================================================

    @Nested
    @DisplayName("login()")
    class Login {

        @Test
        @DisplayName("valid credentials — returns AuthResponse with access token and refresh token")
        void login_validCredentials_returnsTokens() {
            // Arrange
            LoginRequest req = new LoginRequest(USERNAME, PASSWORD);

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches(PASSWORD, HASH)).thenReturn(true);
            when(jwtUtil.generateAccessToken(savedUser)).thenReturn(ACCESS_JWT);
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));

            // Act
            AuthResponse response = authService.login(req);

            // Assert — both tokens are present and non-blank
            assertThat(response).isNotNull();
            assertThat(response.accessToken()).isEqualTo(ACCESS_JWT);
            assertThat(response.refreshToken()).isNotBlank();

            // Assert — a refresh token record was persisted
            ArgumentCaptor<RefreshToken> tokenCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository).save(tokenCaptor.capture());
            RefreshToken persisted = tokenCaptor.getValue();

            // The raw token must NOT be stored — only its hash
            assertThat(persisted.getTokenHash()).isNotEqualTo(response.refreshToken());
            assertThat(persisted.getTokenHash()).hasSize(64); // SHA-256 hex = 64 chars
            assertThat(persisted.isRevoked()).isFalse();
            assertThat(persisted.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("unknown username — throws UnauthorizedException with generic message")
        void login_unknownUsername_throwsUnauthorizedException() {
            // Arrange
            LoginRequest req = new LoginRequest("unknown", PASSWORD);
            when(userRepository.findByUsername("unknown")).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(UnauthorizedException.class)
                    // Message must be generic — must not say "username not found"
                    .hasMessage("Invalid credentials.");

            verify(passwordEncoder, never()).matches(anyString(), anyString());
        }

        @Test
        @DisplayName("wrong password — throws UnauthorizedException with generic message")
        void login_wrongPassword_throwsUnauthorizedException() {
            // Arrange
            LoginRequest req = new LoginRequest(USERNAME, "wrongpassword");

            when(userRepository.findByUsername(USERNAME)).thenReturn(Optional.of(savedUser));
            when(passwordEncoder.matches("wrongpassword", HASH)).thenReturn(false);

            // Act & Assert
            assertThatThrownBy(() -> authService.login(req))
                    .isInstanceOf(UnauthorizedException.class)
                    // Message must be identical to the unknown-username case (Req 1.4)
                    .hasMessage("Invalid credentials.");

            verify(jwtUtil, never()).generateAccessToken(any());
            verify(refreshTokenRepository, never()).save(any());
        }
    }

    // =========================================================================
    // refresh()
    // =========================================================================

    @Nested
    @DisplayName("refresh()")
    class Refresh {

        /**
         * Computes the SHA-256 hex of a string — mirrors the private helper in AuthService
         * so tests can construct matching token hashes without exposing the implementation.
         */
        private String sha256Hex(String input) {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] bytes = digest.digest(input.getBytes(StandardCharsets.UTF_8));
                return HexFormat.of().formatHex(bytes);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Test
        @DisplayName("valid token — revokes old token and returns new access + refresh tokens")
        void refresh_validToken_rotatesAndReturnsNewTokens() {
            // Arrange
            String rawToken = "raw-refresh-token-uuid";
            String tokenHash = sha256Hex(rawToken);

            RefreshToken stored = RefreshToken.builder()
                    .id(10L)
                    .userId(1L)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(29, ChronoUnit.DAYS))
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(stored));
            when(refreshTokenRepository.save(any(RefreshToken.class)))
                    .thenAnswer(inv -> inv.getArgument(0));
            when(userRepository.findById(1L)).thenReturn(Optional.of(savedUser));
            when(jwtUtil.generateAccessToken(savedUser)).thenReturn(ACCESS_JWT);

            // Act
            AuthResponse response = authService.refresh(rawToken);

            // Assert — new tokens are returned
            assertThat(response.accessToken()).isEqualTo(ACCESS_JWT);
            assertThat(response.refreshToken()).isNotBlank();
            // New refresh token must differ from the old raw token
            assertThat(response.refreshToken()).isNotEqualTo(rawToken);

            // Assert — old token was revoked (first save call)
            ArgumentCaptor<RefreshToken> saveCaptor = ArgumentCaptor.forClass(RefreshToken.class);
            verify(refreshTokenRepository, times(2)).save(saveCaptor.capture());

            RefreshToken revokedToken = saveCaptor.getAllValues().get(0);
            assertThat(revokedToken.isRevoked()).isTrue();

            // Assert — new token record was persisted with correct userId
            RefreshToken newToken = saveCaptor.getAllValues().get(1);
            assertThat(newToken.getUserId()).isEqualTo(1L);
            assertThat(newToken.isRevoked()).isFalse();
            assertThat(newToken.getExpiresAt()).isAfter(Instant.now());
        }

        @Test
        @DisplayName("revoked token — throws UnauthorizedException")
        void refresh_revokedToken_throwsUnauthorizedException() {
            // Arrange
            String rawToken = "revoked-token";
            String tokenHash = sha256Hex(rawToken);

            RefreshToken revoked = RefreshToken.builder()
                    .id(11L)
                    .userId(1L)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().plus(10, ChronoUnit.DAYS))
                    .revoked(true)
                    .build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(revoked));

            // Act & Assert
            assertThatThrownBy(() -> authService.refresh(rawToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid or expired refresh token.");

            verify(userRepository, never()).findById(any());
            verify(jwtUtil, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("expired token — throws UnauthorizedException")
        void refresh_expiredToken_throwsUnauthorizedException() {
            // Arrange
            String rawToken = "expired-token";
            String tokenHash = sha256Hex(rawToken);

            RefreshToken expired = RefreshToken.builder()
                    .id(12L)
                    .userId(1L)
                    .tokenHash(tokenHash)
                    .expiresAt(Instant.now().minus(1, ChronoUnit.DAYS)) // already expired
                    .revoked(false)
                    .build();

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(expired));

            // Act & Assert
            assertThatThrownBy(() -> authService.refresh(rawToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid or expired refresh token.");

            verify(userRepository, never()).findById(any());
            verify(jwtUtil, never()).generateAccessToken(any());
        }

        @Test
        @DisplayName("unknown token hash — throws UnauthorizedException")
        void refresh_unknownToken_throwsUnauthorizedException() {
            // Arrange
            String rawToken = "completely-unknown-token";
            String tokenHash = sha256Hex(rawToken);

            when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.empty());

            // Act & Assert
            assertThatThrownBy(() -> authService.refresh(rawToken))
                    .isInstanceOf(UnauthorizedException.class)
                    .hasMessage("Invalid or expired refresh token.");
        }
    }
}
