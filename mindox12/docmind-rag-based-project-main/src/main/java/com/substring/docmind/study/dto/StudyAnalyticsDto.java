package com.substring.docmind.study.dto;

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
public class StudyAnalyticsDto {
    @Builder.Default
    private List<Map<String, Object>> dailyPerformance = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> weeklyPerformance = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> monthlyPerformance = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> yearlyPerformance = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> consistencyGraph = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> actualVsPlanned = new ArrayList<>();
    
    private double deepWorkRatio;
    private String peakFocusHour;
    private int averageSessionMinutes;
    private double focusEfficiencyScore;
    private int consistencyIndex;
}
