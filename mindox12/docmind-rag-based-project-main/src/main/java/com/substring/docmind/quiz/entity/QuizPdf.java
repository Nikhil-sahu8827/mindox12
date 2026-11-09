package com.substring.docmind.quiz.entity;

import com.substring.docmind.audit.DateAudit;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "quiz_pdfs", indexes = {
        @Index(name = "idx_quiz_pdfs_quiz_id", columnList = "quiz_id", unique = true)
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QuizPdf extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "quiz_id", nullable = false, unique = true)
    private Quiz quiz;

    @Column(name = "file_name", nullable = false)
    private String fileName;

    @Column(name = "total_questions", nullable = false)
    @Builder.Default
    private int totalQuestions = 0;

    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARBINARY)
    @Column(name = "pdf_data", nullable = false)
    private byte[] pdfData;

    @Column(name = "file_size_bytes")
    private Long fileSizeBytes;
}
