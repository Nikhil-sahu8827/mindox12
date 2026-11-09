package com.substring.docmind.growth.entity;

import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "question_attempts", indexes = {
        @Index(name = "idx_attempts_user_id", columnList = "user_id"),
        @Index(name = "idx_attempts_question_id", columnList = "question_id"),
        @Index(name = "idx_attempts_topic_name", columnList = "topic_name"),
        @Index(name = "idx_attempts_doc_id", columnList = "document_id"),
        @Index(name = "idx_attempts_created_at", columnList = "created_at")
})
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuestionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "question_id", nullable = false)
    private QuizQuestion question;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id")
    private Quiz quiz;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "document_id", length = 64)
    private String documentId;

    @Column(name = "source_file_name", length = 255)
    private String sourceFileName;

    @Column(name = "selected_option")
    private Integer selectedOption;

    @Column(name = "is_correct", nullable = false)
    private boolean isCorrect;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
