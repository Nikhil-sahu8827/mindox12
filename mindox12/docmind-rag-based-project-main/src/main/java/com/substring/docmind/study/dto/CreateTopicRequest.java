package com.substring.docmind.study.dto;

import lombok.Data;

@Data
public class CreateTopicRequest {
    private String name;
    private Double estimatedHours;
}
