package com.substring.docmind.document.service;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.enums.DocumentStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final VectorStore vectorStore;
    private final DocumentMetadataRepository documentMetadataRepository;

    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper = new com.fasterxml.jackson.databind.ObjectMapper();

    private void persistChunksToDb(List<Document> chunks, String documentId) {
        if (jdbcTemplate == null || chunks == null || chunks.isEmpty()) return;
        try {
            if (isVectorStoreTablePresent()) {
                for (Document chunk : chunks) {
                    String chunkId = chunk.getId() != null ? chunk.getId() : java.util.UUID.randomUUID().toString();
                    String metaJson = objectMapper.writeValueAsString(chunk.getMetadata());
                    jdbcTemplate.update(
                            "INSERT INTO vector_store (id, content, metadata) VALUES (?, ?, ?::jsonb) " +
                            "ON CONFLICT (id) DO UPDATE SET content = EXCLUDED.content, metadata = EXCLUDED.metadata",
                            chunkId, chunk.getText(), metaJson
                    );
                }
                log.info("Persisted {} chunks to PostgreSQL vector_store for document: {}", chunks.size(), documentId);
            }
        } catch (Exception e) {
            log.warn("Notice: could not persist chunks to vector_store table: {}", e.getMessage());
        }
    }

    @Async
    public void ingestChunksAsync(String documentId, List<Document> chunks) {
        log.info("Starting asynchronous ingestion for document: {} (chunks: {})", documentId, chunks.size());

        try {
            if (!chunks.isEmpty()) {
                vectorStore.add(chunks);
                persistChunksToDb(chunks, documentId);
                log.info("Successfully embedded and saved {} chunks to vector store for document: {}", chunks.size(), documentId);
            }
            updateStatus(documentId, DocumentStatus.INDEXED, chunks.size());
        } catch (Exception e) {
            log.error("Failed to ingest document {} into vector store: {}", documentId, e.getMessage(), e);
            updateStatus(documentId, DocumentStatus.FAILED, 0);
        }
    }

    public void ingestChunksSync(String documentId, List<Document> chunks) {
        log.info("Starting synchronous ingestion for document: {} (chunks: {})", documentId, chunks.size());

        try {
            if (!chunks.isEmpty()) {
                vectorStore.add(chunks);
                persistChunksToDb(chunks, documentId);
                log.info("Successfully embedded and saved {} chunks to vector store for document: {}", chunks.size(), documentId);
            }
            updateStatus(documentId, DocumentStatus.INDEXED, chunks.size());
        } catch (Exception e) {
            log.error("Failed to ingest document {} into vector store: {}", documentId, e.getMessage(), e);
            updateStatus(documentId, DocumentStatus.FAILED, 0);
        }
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void updateStatus(String documentId, DocumentStatus status, int totalChunks) {
        findDocumentWithRetry(documentId).ifPresentOrElse(doc -> {
            doc.setStatus(status);
            if (status == DocumentStatus.PROCESSED || status == DocumentStatus.INDEXED) {
                doc.setTotalChunks(totalChunks);
            }
            documentMetadataRepository.saveAndFlush(doc);
            log.info("Successfully updated document {} status to {}", documentId, status);
        }, () -> log.error("Document {} could not be found to update status to {}", documentId, status));
    }

    private java.util.Optional<DocumentMetadata> findDocumentWithRetry(String documentId) {
        for (int i = 0; i < 5; i++) {
            java.util.Optional<DocumentMetadata> doc = documentMetadataRepository.findById(documentId);
            if (doc.isPresent()) {
                return doc;
            }
            try {
                Thread.sleep(300);
            } catch (InterruptedException ignored) {}
        }
        return java.util.Optional.empty();
    }

    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    private boolean isVectorStoreTablePresent() {
        if (jdbcTemplate == null) return false;
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store')",
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            return false;
        }
    }

    public void deleteDocumentVectors(String documentId) {
        log.info("Deleting vector chunks associated with document: {}", documentId);
        try {
            if (isVectorStoreTablePresent()) {
                int count = jdbcTemplate.update("DELETE FROM vector_store WHERE metadata->>'documentId' = ?", documentId);
                log.info("Successfully deleted {} vector chunks for document: {}", count, documentId);
            } else {
                log.info("Table 'vector_store' does not exist in database, skipping SQL vector deletion");
            }
        } catch (Exception e) {
            log.warn("Could not delete vectors from vector_store for document {}: {}", documentId, e.getMessage());
        }
    }
}
