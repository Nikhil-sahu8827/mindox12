package com.substring.docmind.growth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalizedRecommendationDto {

    private String id;
    private String type; // REVISE_WEAK, PRACTICE_AGAIN, REVISIT_DECLINED, ADVANCE_NEXT, DAILY_TARGET
    private String priority; // HIGH, MEDIUM, LOW
    private String title;
    private String reason;
    private String topicName;
    private String documentId;
    private String sourceFileName;
    private String actionLabel; // "Practice This Topic", "Revise Mistakes", etc.
    private double currentStrength;
    private double currentAccuracy;
}
