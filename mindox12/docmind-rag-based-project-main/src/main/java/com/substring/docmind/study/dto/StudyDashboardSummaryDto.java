package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StudyDashboardSummaryDto {
    private double todayHours;
    private double weeklyHours;
    private double monthlyHours;
    private double yearlyHours;
    private double todayTargetHours;
    private double remainingTodayHours;
    private int currentStreak;
    private int completedCoursesCount;
    private String currentStatus;
    
    @Builder.Default
    private List<CourseDto> courses = new ArrayList<>();
    private AiPlannerDto aiPlanner;
    private StudyAnalyticsDto analytics;
    private CalendarHeatmapDto calendarHeatmap;
    @Builder.Default
    private List<AchievementDto> achievements = new ArrayList<>();
    @Builder.Default
    private List<NotificationDto> notifications = new ArrayList<>();
}
