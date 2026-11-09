package com.substring.docmind.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicGrowthDashboardDto {

    // High Level Overview
    private double overallCompletionPercentage;
    private double overallAccuracy;
    private double overallAverageStrength;
    private int totalAvailableQuestions;
    private int totalUniqueQuestionsAttempted;
    private int totalPracticeSessions;
    private int totalAttemptsCount;
    private int totalCorrectAnswers;
    private int totalIncorrectAnswers;
    private int unattemptedQuestions;

    // Strength breakdown counts
    private int beginnerCount;
    private int developingCount;
    private int intermediateCount;
    private int strongCount;
    private int excellentCount;
    private int notEnoughDataCount;

    // Total counts
    private int totalTopicsCount;
    private int totalFilesCount;
    private int dailyPracticeTarget;

    // Categorized lists
    @Builder.Default
    private List<TopicHierarchyDto> strongTopics = new ArrayList<>();

    @Builder.Default
    private List<TopicHierarchyDto> weakTopics = new ArrayList<>();

    @Builder.Default
    private List<TopicHierarchyDto> needsPracticeTopics = new ArrayList<>();

    @Builder.Default
    private List<TopicHierarchyDto> mostImprovedTopics = new ArrayList<>();

    // Full Topic Tree / Hierarchy
    @Builder.Default
    private List<TopicHierarchyDto> allTopics = new ArrayList<>();

    // File-wise progress breakdown
    @Builder.Default
    private List<FileProgressDto> filesProgress = new ArrayList<>();

    // Activity tracking
    @Builder.Default
    private List<ActivitySnapshotDto> dailyActivity = new ArrayList<>();

    @Builder.Default
    private List<ActivitySnapshotDto> weeklyActivity = new ArrayList<>();

    // Historical Strength Over Time (for Line Charts)
    @Builder.Default
    private List<Map<String, Object>> strengthOverTime = new ArrayList<>();

    // Accuracy Distribution by Topic (for Bar Charts)
    @Builder.Default
    private List<Map<String, Object>> topicAccuracyComparison = new ArrayList<>();

    // Personalized Recommendations
    @Builder.Default
    private List<PersonalizedRecommendationDto> recommendations = new ArrayList<>();
}
