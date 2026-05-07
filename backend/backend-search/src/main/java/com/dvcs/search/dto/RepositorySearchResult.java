package com.dvcs.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * DTO representing a repository search result.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RepositorySearchResult {

    private Long id;
    private String ownerUsername;
    private String name;
    private String description;
    private boolean isPrivate;
    private OffsetDateTime createdAt;
}
