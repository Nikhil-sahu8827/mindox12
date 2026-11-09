package com.substring.docmind.quiz.dto;

import com.substring.docmind.enums.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateQuizRequest {

    private String documentId;

    private java.util.List<String> documentIds;

    @Builder.Default
    private Integer numQuestions = 5;

    private Integer questionCount;

    private java.util.List<String> questionTypes;

    private String questionType;

    private String type;

    private Boolean autoSelectTypes;

    private Integer timerMinutes;

    private Boolean adaptiveMode;

    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.MEDIUM;

    private String subject;

    private String topic;

    private String searchFilter;

    private String youtubeUrl;

    public String getEffectiveYoutubeUrl() {
        if (youtubeUrl != null && !youtubeUrl.isBlank()) {
            return youtubeUrl.trim();
        }
        return null;
    }

    public String getEffectiveDocumentId() {
        if (documentId != null && !documentId.isBlank()) {
            return documentId;
        }
        if (documentIds != null && !documentIds.isEmpty()) {
            return documentIds.get(0);
        }
        return null;
    }

    public java.util.List<String> getEffectiveDocumentIds() {
        if (documentIds != null && !documentIds.isEmpty()) {
            return documentIds.stream().filter(id -> id != null && !id.isBlank()).distinct().collect(java.util.stream.Collectors.toList());
        }
        if (documentId != null && !documentId.isBlank()) {
            return java.util.Collections.singletonList(documentId);
        }
        return java.util.Collections.emptyList();
    }

    public int getEffectiveNumQuestions() {
        if (questionCount != null && questionCount > 0) {
            return Math.min(20, questionCount);
        }
        if (numQuestions != null && numQuestions > 0) {
            return Math.min(20, numQuestions);
        }
        return 5;
    }

    public java.util.List<String> getEffectiveQuestionTypes() {
        java.util.List<String> types = new java.util.ArrayList<>();
        if (questionTypes != null && !questionTypes.isEmpty()) {
            for (String t : questionTypes) {
                if (t != null && !t.isBlank()) {
                    types.add(normalizeQuestionType(t));
                }
            }
        }
        if (types.isEmpty() && questionType != null && !questionType.isBlank()) {
            types.add(normalizeQuestionType(questionType));
        }
        if (types.isEmpty() && type != null && !type.isBlank()) {
            types.add(normalizeQuestionType(type));
        }
        if (types.isEmpty()) {
            types.add("MCQ");
        }
        return types.stream().distinct().collect(java.util.stream.Collectors.toList());
    }

    public static String normalizeQuestionType(String raw) {
        if (raw == null) return "MCQ";
        String upper = raw.trim().toUpperCase().replace(" ", "_").replace("-", "_").replace("/", "_");
        if (upper.contains("TRUE") || upper.contains("FALSE")) return "TRUE_FALSE";
        if (upper.contains("SHORT") || upper.contains("ANSWER") || upper.contains("FILL") || upper.contains("BLANK")) return "SHORT_ANSWER";
        if (upper.contains("OUTPUT")) return "OUTPUT_PREDICTION";
        if (upper.contains("DEBUG")) return "DEBUGGING";
        if (upper.contains("CODE")) return "CODE";
        if (upper.contains("CONCEPTUAL")) return "CONCEPTUAL";
        if (upper.contains("SENTENCE") || upper.contains("CORRECTION")) return "SENTENCE_CORRECTION";
        return "MCQ";
    }
}
