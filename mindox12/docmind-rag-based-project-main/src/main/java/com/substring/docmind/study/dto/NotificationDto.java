package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationDto {
    private String id;
    private String type; // "DEADLINE", "REMINDER", "ACHIEVEMENT", "WARNING"
    private String title;
    private String message;
    private String timeAgo;
    private boolean urgent;
}
