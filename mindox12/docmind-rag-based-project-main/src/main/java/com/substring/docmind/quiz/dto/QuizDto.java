package com.substring.docmind.quiz.dto;

import com.substring.docmind.enums.DifficultyLevel;
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
public class QuizDto {

    private String id;
    private String title;
    private String documentId;
    private DifficultyLevel difficulty;
    private Integer totalQuestions;
    private boolean completed;
    private Integer score;
    private LocalDateTime createdAt;
    @Builder.Default
    private List<QuizQuestionDto> questions = new ArrayList<>();

    @Builder.Default
    private Boolean pdfAvailable = false;
    private String pdfFileName;
    private String pdfDownloadUrl;
    private String pdfPreviewUrl;
    private Integer pdfQuestionsCount;
}
