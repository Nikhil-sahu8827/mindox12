package com.substring.docmind.study.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "user_study_stats", uniqueConstraints = {
        @UniqueConstraint(columnNames = "user_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserStudyStats extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(name = "daily_target_hours")
    @Builder.Default
    private Double dailyTargetHours = 4.0;

    @Column(name = "current_streak")
    @Builder.Default
    private Integer currentStreak = 1;

    @Column(name = "longest_streak")
    @Builder.Default
    private Integer longestStreak = 1;

    @Column(name = "last_study_date")
    private LocalDate lastStudyDate;
}
