package com.dvcs.repository.dto;

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
public record TreeEntryDto(
        String name,
        String type,
        long size,
        String lastCommitSha,
        String lastCommitMessage
) {}
