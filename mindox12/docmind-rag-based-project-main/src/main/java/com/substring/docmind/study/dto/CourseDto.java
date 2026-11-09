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
public class CourseDto {
    private Long id;
    private String title;
    private String category;
    private String description;
    private LocalDate targetDeadline;
    private Double dailyTargetHours;
    private String colorAccent;
    private String status;
    private double completionPercentage;
    private int totalTopics;
    private int completedTopics;
    private int remainingTopics;
    private double remainingEstimatedHours;
    private LocalDate predictedCompletionDate;
    private String deadlineStatus; // "ON_TRACK", "AT_RISK", "BEHIND"
    @Builder.Default
    private List<CourseTopicDto> topics = new ArrayList<>();
}
