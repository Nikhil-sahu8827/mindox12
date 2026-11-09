package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AchievementDto {
    private String id;
    private String title;
    private String description;
    private String icon;
    private boolean unlocked;
    private String unlockedAt;
    private String progressText;
}
