package com.substring.docmind.growth.entity;

import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "topic_progress_snapshots", indexes = {
        @Index(name = "idx_snapshots_user_topic", columnList = "user_id, topic_name"),
        @Index(name = "idx_snapshots_date", columnList = "snapshot_date"),
        @Index(name = "idx_snapshots_user_id", columnList = "user_id")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TopicProgressSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "document_id", length = 64)
    private String documentId;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "strength_score", nullable = false)
    private Double strengthScore;

    @Column(nullable = false)
    private Double accuracy;

    @Column(name = "questions_attempted", nullable = false)
    private Integer questionsAttempted;

    @Column(name = "questions_correct", nullable = false)
    private Integer questionsCorrect;

    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
