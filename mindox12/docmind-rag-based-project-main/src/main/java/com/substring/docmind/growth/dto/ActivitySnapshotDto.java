package com.substring.docmind.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActivitySnapshotDto {

    private LocalDate date;
    private String label; // "Mon", "Oct 28"
    private int questionsAttempted;
    private int correctAnswers;
    private double accuracyPercentage;
    private double averageStrength;
}
