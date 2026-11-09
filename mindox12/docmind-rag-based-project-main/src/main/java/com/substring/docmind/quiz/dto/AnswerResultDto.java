package com.substring.docmind.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnswerResultDto {

    private Long questionId;
    private Integer selectedOption;
    private String selectedAnswer;
    private String correctAnswer;
    private Integer correctAnswerIndex;

    @JsonProperty("correct")
    private Boolean correct;

    @JsonProperty("isCorrect")
    private Boolean isCorrect;

    private String explanation;
    private String yourMistakeExplanation;
    private String keyPoint;
    private Integer currentScore;
    private Integer totalAnswered;
    private Integer totalQuestions;
    private Boolean quizCompleted;

    public boolean isCorrect() {
        return Boolean.TRUE.equals(isCorrect) || Boolean.TRUE.equals(correct);
    }
}
