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
public class FileProgressDto {

    private String documentId;
    private String fileName;
    private String contentType;
    private Long fileSize;
    private Integer totalPages;
    private int totalTopics;
    private int totalAvailableQuestions;
    private int uniqueAttemptedQuestions;
    private int totalAttempts;
    private int correctAnswers;
    private int incorrectAnswers;
    private double completionPercentage;
    private double accuracyPercentage;
    private double strengthScore;
    private String strengthLevel;
    private LocalDateTime lastPracticedAt;

    @Builder.Default
    private List<TopicHierarchyDto> topics = new ArrayList<>();
}
