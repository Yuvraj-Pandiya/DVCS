package com.dvcs.repository.dto;

import java.time.OffsetDateTime;

/**
 * Data transfer object representing commit metadata.
 */
public record CommitMetaDto(
        Long id,
        String sha,
        Long authorId,
        String authorUsername,
        String message,
        OffsetDateTime authoredAt,
        OffsetDateTime committedAt
) {}
