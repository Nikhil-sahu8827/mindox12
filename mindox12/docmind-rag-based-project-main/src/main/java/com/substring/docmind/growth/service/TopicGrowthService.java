package com.substring.docmind.growth.service;

import com.substring.docmind.growth.dto.PersonalizedRecommendationDto;
import com.substring.docmind.growth.dto.TopicGrowthDashboardDto;
import com.substring.docmind.growth.dto.TopicHierarchyDto;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.security.UserPrincipal;

import java.util.List;
import java.util.Map;

public interface TopicGrowthService {

    /**
     * Retrieves the complete Topic Growth & Performance Dashboard for the current user.
     */
    TopicGrowthDashboardDto getGrowthDashboard(String timeRange, String documentId, UserPrincipal currentUser);

    /**
     * Retrieves all study topics with hierarchy and user-specific performance metrics.
     */
    List<TopicHierarchyDto> getTopicHierarchy(String documentId, UserPrincipal currentUser);

    /**
     * Retrieves detailed practice attempt history and snapshots for a specific topic.
     */
    Map<String, Object> getTopicPracticeHistory(Long topicId, UserPrincipal currentUser);

    /**
     * Generates personalized learning and revision recommendations based on real performance.
     */
    List<PersonalizedRecommendationDto> getRecommendations(UserPrincipal currentUser);

    /**
     * Records a practice attempt for historical tracking and updates snapshots.
     */
    void recordQuestionAttempt(Long userId, QuizQuestion question, Integer selectedOption, boolean isCorrect, String documentId, String sourceFileName);
}
