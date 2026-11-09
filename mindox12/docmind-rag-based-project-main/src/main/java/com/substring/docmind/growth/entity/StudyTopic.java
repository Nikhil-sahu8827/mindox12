package com.substring.docmind.growth.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "study_topics", indexes = {
        @Index(name = "idx_topics_document_id", columnList = "document_id"),
        @Index(name = "idx_topics_user_id", columnList = "user_id"),
        @Index(name = "idx_topics_normalized_key", columnList = "normalized_key"),
        @Index(name = "idx_topics_parent_id", columnList = "parent_topic_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudyTopic extends DateAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id")
    @org.hibernate.annotations.OnDelete(action = org.hibernate.annotations.OnDeleteAction.CASCADE)
    private DocumentMetadata document;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "source_file_name", nullable = false, length = 255)
    private String sourceFileName;

    @Column(name = "topic_name", nullable = false, length = 255)
    private String topicName;

    @Column(name = "subtopic_name", length = 255)
    private String subtopicName;

    @Column(name = "hierarchy_level", nullable = false)
    @Builder.Default
    private Integer hierarchyLevel = 1; // 1 = Topic, 2 = Subtopic

    @Column(name = "parent_topic_id")
    private Long parentTopicId;

    @Column(name = "normalized_key", nullable = false, length = 255)
    private String normalizedKey;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "estimated_total_questions", nullable = false)
    @Builder.Default
    private Integer estimatedTotalQuestions = 20;

    @Column(name = "is_global", nullable = false)
    @Builder.Default
    private boolean isGlobal = true;

    @Column(name = "order_index")
    @Builder.Default
    private Integer orderIndex = 0;
}
