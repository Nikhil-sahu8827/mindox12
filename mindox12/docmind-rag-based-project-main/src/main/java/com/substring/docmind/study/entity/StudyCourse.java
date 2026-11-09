package com.substring.docmind.study.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "study_courses", indexes = {
        @Index(name = "idx_study_courses_user", columnList = "user_id"),
        @Index(name = "idx_study_courses_status", columnList = "status")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyCourse extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(length = 100)
    private String category;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "target_deadline")
    private LocalDate targetDeadline;

    @Column(name = "daily_target_hours")
    @Builder.Default
    private Double dailyTargetHours = 2.0;

    @Column(name = "color_accent", length = 30)
    @Builder.Default
    private String colorAccent = "#6366f1";

    @Column(length = 30)
    @Builder.Default
    private String status = "ACTIVE";

    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("orderIndex ASC")
    @Builder.Default
    private List<CourseTopic> topics = new ArrayList<>();
}
