package com.substring.docmind.quiz.dto;

import com.substring.docmind.enums.DifficultyLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizHistoryDto {

    private String id;
    private String title;
    private String documentTitle;
    private DifficultyLevel difficulty;
    private Integer score;
    private Integer totalQuestions;
    private Double percentage;
    private LocalDateTime completedAt;
    private String pdfDownloadUrl;
    private String pdfPreviewUrl;
}
