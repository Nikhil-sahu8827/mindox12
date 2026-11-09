package com.substring.docmind.study.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "study_session_records", indexes = {
        @Index(name = "idx_sessions_user_date", columnList = "user_id, session_date")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudySessionRecord extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "course_id")
    private Long courseId;

    @Column(name = "course_title", length = 200)
    private String courseTitle;

    @Column(name = "topic_name", length = 255)
    private String topicName;

    @Column(name = "duration_minutes", nullable = false)
    private Integer durationMinutes;

    @Column(name = "session_type", length = 50)
    @Builder.Default
    private String sessionType = "STOPWATCH";

    @Column(name = "session_date", nullable = false)
    private LocalDate sessionDate;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "ended_at")
    private LocalDateTime endedAt;

    @Column(columnDefinition = "TEXT")
    private String notes;
}
