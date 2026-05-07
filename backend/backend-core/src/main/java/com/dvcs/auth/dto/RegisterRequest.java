package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for user registration.
 */
@Schema(description = "Request body for registering a new user account")
public record RegisterRequest(

        @Schema(description = "Unique username for the new account", example = "alice")
        @NotBlank(message = "Username must not be blank")
        String username,

        @Schema(description = "Email address for the new account", example = "alice@example.com")
        @NotBlank(message = "Email must not be blank")
        @Email(message = "Email must be a valid email address")
        String email,

        @Schema(description = "Password for the new account (min 8 characters recommended)", example = "s3cr3tP@ssw0rd")
        @NotBlank(message = "Password must not be blank")
        String password
) {}
