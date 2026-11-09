package com.substring.docmind.quiz.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizPdfStatusDto {
    private String quizId;
    private String fileName;
    private int totalQuestions;
    private long fileSizeBytes;
    private boolean available;
    private LocalDateTime lastUpdated;
    private String downloadUrl;
    private String previewUrl;
}
