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
public class CalendarHeatmapDto {
    @Builder.Default
    private List<Map<String, Object>> monthlyDays = new ArrayList<>();
    @Builder.Default
    private List<Map<String, Object>> yearlyHeatmap = new ArrayList<>();
    private int totalActiveDays;
    private int longestStreak;
    private double maxDailyHours;
}
