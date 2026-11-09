package com.substring.docmind.document.dto.youtube;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class YouTubeLearningMaterialsDto {
    private String videoId;
    private String title;
    private String summary;
    private List<SectionSummary> sectionSummaries;
    private List<TopicNote> topicNotes;
    private List<String> importantTopics;
    private List<String> technicalTerms;
    private List<ConceptExplanation> conceptExplanations;
    private List<VideoQuestion> questions;
    private List<VideoQuizQuestion> quizQuestions;
    private List<VideoFlashcard> flashcards;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SectionSummary {
        private String timeRange; // e.g. "[00:02:15 - 00:08:40]"
        private String title;
        private String keyPoints;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TopicNote {
        private String topic;
        private String timestamp; // e.g. "[12:45]"
        private String definition;
        private String detailedExplanation;
        private String example;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ConceptExplanation {
        private String concept;
        private String timestamp;
        private String simpleLanguageExplanation;
        private String analogy;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoQuestion {
        private String question;
        private String answer;
        private String timestamp; // citation in video
        private String questionType; // CONCEPTUAL, RECALL, CRITICAL_THINKING
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoQuizQuestion {
        private String question;
        private List<String> options;
        private int correctOptionIndex;
        private String explanation;
        private String questionType; // MCQ, TRUE_FALSE
        private String timestamp;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class VideoFlashcard {
        private String front; // Question or Concept
        private String back;  // Answer or Explanation
        private String topic;
        private String timestamp;
    }
}
