package com.substring.docmind.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizResultDto {

    private String quizId;
    private Integer score;
    private Integer totalQuestions;
    private Double percentage;
    @Builder.Default
    private List<QuizQuestionDto> questions = new ArrayList<>();
}
