package com.substring.docmind.chat.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimilaritySearchRequest {

    @NotBlank(message = "Query cannot be empty")
    private String query;

    private String documentId;

    @Builder.Default
    private int topK = 5;
}
