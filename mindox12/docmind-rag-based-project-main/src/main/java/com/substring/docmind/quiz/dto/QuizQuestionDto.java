package com.substring.docmind.quiz.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
public class QuizQuestionDto {

    private Long id;
    private String question;
    private String questionText;
    private String sourceDocumentName;
    private String codeSnippet;

    public String getQuestionText() {
        return questionText != null ? questionText : question;
    }

    @Builder.Default
    private List<String> options = new ArrayList<>();

    private String correctAnswer;
    private Integer correctAnswerIndex;
    private String explanation;
    private Integer userAnswer;
    private String selectedAnswer;

    @JsonProperty("isCorrect")
    private Boolean isCorrect;

    @JsonProperty("correct")
    private Boolean correct;

    @JsonProperty("isSaved")
    private Boolean isSaved;

    @JsonProperty("saved")
    private Boolean saved;

    private String difficulty;
    private String topic;

    @Builder.Default
    private String type = "MCQ";

    private boolean answered;
}
