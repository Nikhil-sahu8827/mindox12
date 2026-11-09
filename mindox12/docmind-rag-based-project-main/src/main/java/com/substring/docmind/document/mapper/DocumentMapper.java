package com.substring.docmind.document.mapper;

import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.entity.DocumentMetadata;
import org.springframework.stereotype.Component;

@Component
public class DocumentMapper {

    public DocumentDto toDto(DocumentMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        com.substring.docmind.enums.DocumentStatus status = metadata.getStatus();
        if (status == com.substring.docmind.enums.DocumentStatus.PROCESSED) {
            status = com.substring.docmind.enums.DocumentStatus.INDEXED;
        }

        return DocumentDto.builder()
                .id(metadata.getId())
                .filename(metadata.getFilename())
                .contentType(metadata.getContentType())
                .fileSize(metadata.getFileSize())
                .status(status)
                .totalPages(metadata.getTotalPages())
                .totalChunks(metadata.getTotalChunks())
                .chunksCreated(metadata.getTotalChunks())
                .sourceUrl(metadata.getSourceUrl())
                .createdAt(metadata.getCreatedAt())
                .build();
    }
}
