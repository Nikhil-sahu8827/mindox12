package com.substring.docmind.document.service;

import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.dto.IngestUrlRequest;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.mapper.DocumentMapper;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.enums.DocumentStatus;
import com.substring.docmind.exception.BadRequestException;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentServiceImpl implements DocumentService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final UserRepository userRepository;
    private final DocumentParserService parserService;
    private final DocumentIngestionService ingestionService;
    private final DocumentMapper documentMapper;
    private final YouTubeService youTubeService;
    private final org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.substring.docmind.growth.service.TopicExtractionService topicExtractionService;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private com.substring.docmind.quiz.repository.QuizRepository quizRepository;

    @Override
    @Transactional
    public DocumentDto uploadDocument(MultipartFile file, UserPrincipal currentUser) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Uploaded file cannot be empty");
        }

        User user = getUser(currentUser.getId());
        String documentId = UUID.randomUUID().toString();
        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : "document_" + documentId;
        String contentType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";

        log.info("Uploading document: {} (size: {} bytes) for user: {}", originalFilename, file.getSize(), user.getId());

        int pageCount = parserService.extractPageCount(file);
        String text = parserService.extractText(file);
        List<Document> chunks = parserService.chunkText(text, documentId, user.getId(), originalFilename, contentType, pageCount, false);

        DocumentMetadata metadata = DocumentMetadata.builder()
                .id(documentId)
                .user(user)
                .filename(originalFilename)
                .contentType(contentType)
                .fileSize(file.getSize())
                .status(DocumentStatus.PROCESSING)
                .totalPages(pageCount)
                .totalChunks(chunks.size())
                .build();

        DocumentMetadata saved = documentMetadataRepository.saveAndFlush(metadata);

        if (!chunks.isEmpty()) {
            ingestionService.ingestChunksAsync(documentId, chunks);
        } else {
            saved.setStatus(DocumentStatus.INDEXED);
            saved = documentMetadataRepository.saveAndFlush(saved);
        }

        return documentMapper.toDto(saved);
    }

    @Override
    @Transactional
    public List<DocumentDto> uploadMultipleDocuments(List<MultipartFile> files, UserPrincipal currentUser) {
        if (files == null || files.isEmpty()) {
            throw new BadRequestException("File list cannot be empty");
        }

        List<DocumentDto> results = new ArrayList<>();
        for (MultipartFile file : files) {
            if (!file.isEmpty()) {
                results.add(uploadDocument(file, currentUser));
            }
        }
        return results;
    }

    @Override
    @Transactional
    public DocumentDto ingestUrl(IngestUrlRequest request, UserPrincipal currentUser) {
        String url = request.getUrl().trim();

        // If this is a YouTube link, automatically route through the dedicated YouTube ingestion service
        if (youTubeService.isYouTubeUrl(url)) {
            log.info("Detected YouTube URL in ingestUrl request: {}. Routing to YouTubeService.", url);
            return youTubeService.ingestYouTubeVideo(url, currentUser);
        }

        User user = getUser(currentUser.getId());
        String documentId = UUID.randomUUID().toString();

        log.info("Ingesting URL: {} for user: {}", url, user.getId());

        String filename = url.replaceAll("^https?://", "").replaceAll("[^a-zA-Z0-9.-]", "_");
        if (filename.length() > 100) {
            filename = filename.substring(0, 100);
        }

        String text = parserService.extractTextFromUrl(url);
        List<Document> chunks = parserService.chunkText(text, documentId, user.getId(), filename, "text/html");

        DocumentMetadata metadata = DocumentMetadata.builder()
                .id(documentId)
                .user(user)
                .filename(filename)
                .contentType("text/html")
                .sourceUrl(url)
                .fileSize((long) text.getBytes().length)
                .status(DocumentStatus.PROCESSING)
                .totalPages(1)
                .totalChunks(chunks.size())
                .build();

        DocumentMetadata saved = documentMetadataRepository.saveAndFlush(metadata);

        if (!chunks.isEmpty()) {
            ingestionService.ingestChunksSync(documentId, chunks);
            saved.setStatus(DocumentStatus.INDEXED);
            saved.setTotalChunks(chunks.size());
            saved = documentMetadataRepository.saveAndFlush(saved);
        } else {
            saved.setStatus(DocumentStatus.INDEXED);
            saved = documentMetadataRepository.saveAndFlush(saved);
        }

        if (topicExtractionService != null) {
            try {
                topicExtractionService.extractTopicsForDocument(saved, user);
            } catch (Exception e) {
                log.warn("Automatic topic extraction failed during upload: {}", e.getMessage());
            }
        }

        return documentMapper.toDto(saved);
    }

    @Override
    @Transactional
    public DocumentDto ingestYouTube(String url, UserPrincipal currentUser) {
        return youTubeService.ingestYouTubeVideo(url, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getUserDocuments(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        return documentMetadataRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(documentMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public List<DocumentDto> getAllDocuments(UserPrincipal currentUser) {
        return getUserDocuments(currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public DocumentDto getDocumentById(String documentId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        DocumentMetadata metadata = documentMetadataRepository.findByIdAndUser(documentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));
        return documentMapper.toDto(metadata);
    }

    @Override
    @Transactional
    public DocumentDto updateDocument(String documentId, MultipartFile file, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        DocumentMetadata metadata = documentMetadataRepository.findByIdAndUser(documentId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Updated file cannot be empty");
        }

        String originalFilename = file.getOriginalFilename() != null ? file.getOriginalFilename() : metadata.getFilename();
        String contentType = file.getContentType() != null ? file.getContentType() : metadata.getContentType();

        log.info("Updating document {} with new file {} for user {}", documentId, originalFilename, user.getId());

        // 1. Clean previous vectors from vector_store
        ingestionService.deleteDocumentVectors(documentId);

        // 2. Parse and chunk new file
        int pageCount = parserService.extractPageCount(file);
        String text = parserService.extractText(file);
        List<Document> chunks = parserService.chunkText(text, documentId, user.getId(), originalFilename, contentType, pageCount, false);

        // 3. Update metadata
        metadata.setFilename(originalFilename);
        metadata.setContentType(contentType);
        metadata.setFileSize(file.getSize());
        metadata.setTotalPages(pageCount);
        metadata.setTotalChunks(chunks.size());
        metadata.setStatus(DocumentStatus.PROCESSING);
        metadata = documentMetadataRepository.saveAndFlush(metadata);

        if (!chunks.isEmpty()) {
            ingestionService.ingestChunksSync(documentId, chunks);
            metadata.setStatus(DocumentStatus.INDEXED);
            metadata = documentMetadataRepository.saveAndFlush(metadata);
        } else {
            metadata.setStatus(DocumentStatus.INDEXED);
            metadata = documentMetadataRepository.saveAndFlush(metadata);
        }

        if (topicExtractionService != null) {
            try {
                topicExtractionService.deleteTopicsForDocument(documentId);
                topicExtractionService.extractTopicsForDocument(metadata, user);
            } catch (Exception e) {
                log.warn("Automatic topic re-extraction failed during update: {}", e.getMessage());
            }
        }

        log.info("Document {} updated and re-indexed successfully with {} chunks", documentId, chunks.size());
        return documentMapper.toDto(metadata);
    }

    @Override
    @Transactional
    public void deleteDocument(String documentId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        DocumentMetadata metadata = isAdmin
                ? documentMetadataRepository.findById(documentId).orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId))
                : documentMetadataRepository.findByIdAndUser(documentId, user).orElseThrow(() -> new ResourceNotFoundException("Document", "id", documentId));

        log.info("Deleting document {} from PostgreSQL database for user {}", documentId, user.getId());

        // 1. Delete vector embeddings
        ingestionService.deleteDocumentVectors(documentId);

        // 2. Clean child references directly to guarantee no foreign-key constraint violations or rollbacks
        if (jdbcTemplate != null) {
            try {
                jdbcTemplate.update("UPDATE quizzes SET document_id = NULL WHERE document_id = ?", documentId);
                jdbcTemplate.update("UPDATE question_attempts SET document_id = NULL WHERE document_id = ?", documentId);
                jdbcTemplate.update("DELETE FROM topic_progress_snapshots WHERE document_id = ?", documentId);
                jdbcTemplate.update("DELETE FROM study_topics WHERE document_id = ?", documentId);
            } catch (Exception e) {
                log.warn("Child cleanup warning for document {}: {}", documentId, e.getMessage());
            }
        } else {
            if (topicExtractionService != null) {
                try {
                    topicExtractionService.deleteTopicsForDocument(documentId);
                } catch (Exception e) {
                    log.warn("Failed to delete topics for document: {}", e.getMessage());
                }
            }
            if (quizRepository != null) {
                try {
                    quizRepository.unlinkDocument(documentId);
                } catch (Exception e) {
                    log.warn("Failed to unlink quizzes for document: {}", e.getMessage());
                }
            }
        }

        // 3. Delete document metadata entity via repository so JPA persistence context remains in sync
        documentMetadataRepository.delete(metadata);
        documentMetadataRepository.flush();

        log.info("Document {} successfully deleted from PostgreSQL database", documentId);
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
