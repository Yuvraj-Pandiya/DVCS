package com.dvcs.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * DTO representing a user search result.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserSearchResult {

    private Long id;
    private String username;
    private String email;
    private String avatarUrl;
    private String bio;
    private OffsetDateTime createdAt;
}
