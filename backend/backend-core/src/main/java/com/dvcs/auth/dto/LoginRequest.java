package com.dvcs.auth.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Request body for user login.
 */
@Schema(description = "Request body for authenticating an existing user")
public record LoginRequest(

        @Schema(description = "The username of the account to log in", example = "alice")
        @NotBlank(message = "Username must not be blank")
        String username,

        @Schema(description = "The account password", example = "s3cr3tP@ssw0rd")
        @NotBlank(message = "Password must not be blank")
        String password
) {}
