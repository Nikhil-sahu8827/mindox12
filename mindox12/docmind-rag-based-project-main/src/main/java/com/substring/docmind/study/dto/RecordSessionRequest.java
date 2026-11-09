package com.substring.docmind.study.dto;

import lombok.Data;

@Data
public class RecordSessionRequest {
    private Long courseId;
    private String courseTitle;
    private String topicName;
    private Integer durationMinutes;
    private String sessionType;
    private String notes;
}
