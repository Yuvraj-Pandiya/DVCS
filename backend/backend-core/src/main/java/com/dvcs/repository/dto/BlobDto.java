package com.dvcs.repository.dto;

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
public record BlobDto(
        String content,
        long size,
        String encoding,
        String lastCommitSha
) {}
