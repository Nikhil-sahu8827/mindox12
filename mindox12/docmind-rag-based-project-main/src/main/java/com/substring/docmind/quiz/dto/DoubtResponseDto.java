package com.substring.docmind.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DoubtResponseDto {

    private Long questionId;
    private String answer;
    private java.util.List<String> followUpSuggestions;
}
