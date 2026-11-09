package com.substring.docmind.study.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.List;

@Data
public class CreateCourseRequest {
    private String title;
    private String category;
    private String description;
    private LocalDate targetDeadline;
    private Double dailyTargetHours;
    private String colorAccent;
    private List<String> initialTopics;
}
