package com.dvcs.auth.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for user login.
 */
public record LoginRequest(

        @NotBlank(message = "Username must not be blank")
        String username,

        @NotBlank(message = "Password must not be blank")
        String password
) {}
