package com.substring.docmind.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubtopicProgressDto {

    private Long id;
    private String name;
    private String topicName;
    private String sourceFileName;
    private String documentId;
    private int totalAvailableQuestions;
    private int uniqueAttemptedQuestions;
    private int totalAttempts;
    private int correctAnswers;
    private int incorrectAnswers;
    private int unattemptedQuestions;
    private double completionPercentage;
    private double accuracyPercentage;
    private double strengthScore;
    private String strengthLevel; // Beginner, Developing, Intermediate, Strong, Excellent, Not Enough Data
    private String confidence; // High, Moderate, Insufficient
    private LocalDateTime lastPracticedDate;
}
