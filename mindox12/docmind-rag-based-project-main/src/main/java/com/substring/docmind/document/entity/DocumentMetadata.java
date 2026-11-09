package com.substring.docmind.document.entity;

import com.substring.docmind.audit.DateAudit;
import com.substring.docmind.enums.DocumentStatus;
import com.substring.docmind.user.entity.User;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "document_metadata", indexes = {
        @Index(name = "idx_document_user_id", columnList = "user_id"),
        @Index(name = "idx_document_created_at", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentMetadata extends DateAudit {

    @Id
    @Column(length = 64)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "content_type", length = 100)
    private String contentType;

    @Column(name = "file_size")
    private Long fileSize;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    @Builder.Default
    private DocumentStatus status = DocumentStatus.PENDING;

    @Column(name = "total_pages")
    @Builder.Default
    private Integer totalPages = 1;

    @Column(name = "total_chunks")
    @Builder.Default
    private Integer totalChunks = 0;

    @Column(name = "source_url", length = 1000)
    private String sourceUrl;
}
