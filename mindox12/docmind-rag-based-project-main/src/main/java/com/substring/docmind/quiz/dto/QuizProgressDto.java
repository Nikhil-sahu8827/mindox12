package com.substring.docmind.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizProgressDto {

    private long totalQuizzes;
    private long completedQuizzes;
    private double averageScore;
    private long totalMistakes;
    private long savedQuestionsCount;
}
