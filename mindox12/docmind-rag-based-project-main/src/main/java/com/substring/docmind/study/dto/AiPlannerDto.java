package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiPlannerDto {
    private double totalRemainingHours;
    private double requiredDailyHours;
    private double requiredWeeklyHours;
    private LocalDate expectedCompletionDate;
    private String deadlineStatus; // "ON_TRACK", "AT_RISK", "BEHIND"
    private String paceDeficitText;
    @Builder.Default
    private List<String> recommendations = new ArrayList<>();
    @Builder.Default
    private List<RecoveryDayDto> smartRecoveryPlan = new ArrayList<>();
    private String recoverySummary;
}
