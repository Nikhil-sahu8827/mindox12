package com.substring.docmind.quiz.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "quiz_questions")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false)
    private Quiz quiz;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String question;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "quiz_question_options", joinColumns = @JoinColumn(name = "question_id"))
    @Column(name = "option_text", columnDefinition = "TEXT")
    @Builder.Default
    private List<String> options = new ArrayList<>();

    @Column(name = "correct_answer", nullable = false)
    private Integer correctAnswer;

    @Column(columnDefinition = "TEXT")
    private String explanation;

    @Column(name = "user_answer")
    private Integer userAnswer;

    @Column(name = "code_snippet", columnDefinition = "TEXT")
    private String codeSnippet;

    @Column(name = "question_type")
    @Builder.Default
    private String type = "MCQ";

    @Column(name = "topic")
    private String topic;

    @Column(name = "difficulty")
    private String difficulty;
}
