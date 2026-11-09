package com.substring.docmind.study.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseTopicDto {
    private Long id;
    private Long courseId;
    private String name;
    private Double estimatedHours;
    private boolean completed;
    private Integer orderIndex;
}
