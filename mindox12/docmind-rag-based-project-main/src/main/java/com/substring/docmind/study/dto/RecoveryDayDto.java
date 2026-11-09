package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryDayDto {
    private String day;
    private double targetHours;
    private double catchUpAdditionHours;
    private String focusTopic;
}
