package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing a blob (file) returned by the blob API.
 *
 * <p>Requirement 7.2: The System SHALL return the file content, size, encoding,
 * and the last-commit SHA.
 *
 * @param content       the file content encoded as Base64
 * @param size          the size of the raw content in bytes
 * @param encoding      the encoding used for the content field (always {@code "base64"})
 * @param lastCommitSha the SHA of the last commit that touched this file
 */
@Schema(description = "File (blob) content and metadata returned by the blob API")
public record BlobDto(
        @Schema(description = "File content encoded as Base64",
                example = "SGVsbG8sIFdvcmxkIQo=")
        String content,

        @Schema(description = "Size of the raw (decoded) file content in bytes", example = "14")
        long size,

        @Schema(description = "Encoding used for the content field; always 'base64'", example = "base64")
        String encoding,

        @Schema(description = "SHA of the last commit that modified this file",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        String lastCommitSha
) {}
