package com.substring.docmind.document.dto;

import com.substring.docmind.enums.DocumentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DocumentDto {

    private String id;
    private String filename;
    private String contentType;
    private Long fileSize;
    private DocumentStatus status;
    private Integer totalPages;
    private Integer totalChunks;
    private Integer chunksCreated;
    private String sourceUrl;
    private LocalDateTime createdAt;

    public Integer getChunksCreated() {
        return chunksCreated != null ? chunksCreated : totalChunks;
    }
}
