package com.substring.docmind.study.entity;

import com.substring.docmind.audit.DateAudit;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "course_topics", indexes = {
        @Index(name = "idx_course_topics_course", columnList = "course_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CourseTopic extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private StudyCourse course;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "estimated_hours")
    @Builder.Default
    private Double estimatedHours = 2.0;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "order_index")
    @Builder.Default
    private Integer orderIndex = 0;
}
