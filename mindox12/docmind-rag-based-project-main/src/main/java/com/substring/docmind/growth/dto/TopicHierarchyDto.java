package com.substring.docmind.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicHierarchyDto {

    private Long id;
    private String topicName;
    private String normalizedKey;
    private String sourceFileName;
    private String documentId;
    private String description;
    private int hierarchyLevel;

    @Builder.Default
    private List<SubtopicProgressDto> subtopics = new ArrayList<>();

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
    private int totalPracticeSessions;

    // Comparison against previous snapshot
    private Double previousStrengthScore;
    private Double strengthChange; // e.g. +12.5 or -4.0
    private Boolean isImproving;
}
