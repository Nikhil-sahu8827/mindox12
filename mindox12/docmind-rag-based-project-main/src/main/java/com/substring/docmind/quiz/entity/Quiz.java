package com.substring.docmind.quiz.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.enums.DifficultyLevel;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quizzes", indexes = {
        @Index(name = "idx_quizzes_user_id", columnList = "user_id"),
        @Index(name = "idx_quizzes_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Quiz extends DateAudit {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.SET_NULL)
    private DocumentMetadata document;

    @Column(nullable = false, length = 255)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DifficultyLevel difficulty = DifficultyLevel.MEDIUM;

    @Column(name = "total_questions", nullable = false)
    @Builder.Default
    private Integer totalQuestions = 5;

    @Column(nullable = false)
    @Builder.Default
    private boolean completed = false;

    @Column(name = "score")
    @Builder.Default
    private Integer score = 0;

    @Builder.Default
    @OneToMany(mappedBy = "quiz", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private List<QuizQuestion> questions = new ArrayList<>();

    public void setQuestions(List<QuizQuestion> newQuestions) {
        if (this.questions == null) {
            this.questions = new ArrayList<>();
        } else {
            this.questions.clear();
        }
        if (newQuestions != null) {
            this.questions.addAll(newQuestions);
        }
    }
}
