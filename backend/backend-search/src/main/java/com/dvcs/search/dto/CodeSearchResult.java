package com.dvcs.search.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO representing a code search result.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeSearchResult {

    private String repoOwner;
    private String repoName;
    private String filePath;
    private String snippet;
}
