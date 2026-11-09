package com.substring.docmind.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilaritySearchResult {

    private String text;
    private String documentId;
    private String fileName;
    private Double score;
    private Double similarityScore;
    private Integer pageNumber;
    private Integer chunkIndex;
}
