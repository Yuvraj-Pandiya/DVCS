package com.dvcs.auth.dto;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read-only DTO representing metadata about a personal access token.
 *
 * <p>Used for list operations. Does not contain the raw token value or the token hash —
 * neither is ever exposed after the initial creation response.
 */
public record PersonalTokenInfo(
        Long id,
        String name,
        List<String> scopes,
        Instant expiresAt,
        OffsetDateTime createdAt
) {}
