package com.dvcs.repository.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * DTO representing a single entry in a directory listing returned by the tree API.
 *
 * <p>Requirement 7.1: The System SHALL return a directory listing including entry names,
 * types (blob/tree), sizes, and the last-commit SHA that touched each entry.
 *
 * @param name              the entry name (file or directory name)
 * @param type              the entry type: {@code "blob"} for a file, {@code "tree"} for a directory
 * @param size              the size in bytes (0 for tree entries)
 * @param lastCommitSha     the SHA of the last commit that touched this entry
 * @param lastCommitMessage the message of the last commit that touched this entry
 */
@Schema(description = "A single entry in a directory listing returned by the tree API")
public record TreeEntryDto(
        @Schema(description = "File or directory name", example = "README.md")
        String name,

        @Schema(description = "Entry type: 'blob' for a file, 'tree' for a directory", example = "blob")
        String type,

        @Schema(description = "Size of the entry in bytes; 0 for directory (tree) entries", example = "1024")
        long size,

        @Schema(description = "SHA of the last commit that modified this entry",
                example = "a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2c3d4e5f6a1b2")
        String lastCommitSha,

        @Schema(description = "Commit message of the last commit that modified this entry",
                example = "docs: update README with installation instructions")
        String lastCommitMessage
) {}
