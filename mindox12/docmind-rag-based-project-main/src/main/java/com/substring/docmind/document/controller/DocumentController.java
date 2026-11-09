package com.substring.docmind.document.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.dto.IngestUrlRequest;
import com.substring.docmind.document.service.DocumentService;
import com.substring.docmind.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/documents")
@RequiredArgsConstructor
@Tag(name = "Document Management", description = "Endpoints for uploading, parsing, indexing, and managing documents")
public class DocumentController {

    private final DocumentService documentService;
    private final com.substring.docmind.document.service.WebsiteKnowledgeService websiteKnowledgeService;

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload single document", description = "Uploads and parses a document, generating vector embeddings")
    public ResponseEntity<ApiResponse<DocumentDto>> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to upload document by user id: {}", currentUser.getId());
        DocumentDto response = documentService.uploadDocument(file, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Document uploaded successfully", response));
    }

    @PostMapping(value = "/upload-multiple", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Upload multiple documents", description = "Batch uploads documents for RAG processing")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> uploadMultiple(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to upload {} documents by user id: {}", files.size(), currentUser.getId());
        List<DocumentDto> response = documentService.uploadMultipleDocuments(files, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Documents uploaded successfully", response));
    }

    @PostMapping("/ingest-url")
    @Operation(summary = "Ingest webpage by URL", description = "Scrapes, indexes, and embeds web page content for RAG Q&A")
    public ResponseEntity<ApiResponse<DocumentDto>> ingestUrl(
            @Valid @RequestBody IngestUrlRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to ingest URL {} by user id: {}", request.getUrl(), currentUser.getId());
        DocumentDto response = documentService.ingestUrl(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("URL ingested successfully", response));
    }

    @PostMapping("/ingest-youtube")
    @Operation(summary = "Ingest YouTube video by URL", description = "Extracts video details, transcript, and chapters for RAG and quiz generation")
    public ResponseEntity<ApiResponse<DocumentDto>> ingestYouTube(
            @Valid @RequestBody com.substring.docmind.document.dto.IngestYouTubeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to ingest YouTube video URL {} by user id: {}", request.getEffectiveUrl(), currentUser.getId());
        DocumentDto response = documentService.ingestYouTube(request.getEffectiveUrl(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("YouTube video processed and indexed successfully", response));
    }

    @GetMapping
    @Operation(summary = "Get all documents", description = "Retrieves all documents accessible by current user")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> getAllDocuments(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get all documents for user id: {}", currentUser.getId());
        List<DocumentDto> documents = documentService.getAllDocuments(currentUser);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/user")
    @Operation(summary = "Get current user documents", description = "Retrieves documents owned by current authenticated user")
    public ResponseEntity<ApiResponse<List<DocumentDto>>> getUserDocuments(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get user documents for user id: {}", currentUser.getId());
        List<DocumentDto> documents = documentService.getUserDocuments(currentUser);
        return ResponseEntity.ok(ApiResponse.success(documents));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get document by ID", description = "Retrieves details of a specific document")
    public ResponseEntity<ApiResponse<DocumentDto>> getDocumentById(
            @PathVariable("id") String documentId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get document {} for user id: {}", documentId, currentUser.getId());
        DocumentDto document = documentService.getDocumentById(documentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(document));
    }

    @PutMapping(value = "/{id}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Update and re-index document", description = "Replaces file content and automatically updates vector embeddings")
    public ResponseEntity<ApiResponse<DocumentDto>> updateDocument(
            @PathVariable("id") String documentId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to update document {} by user id: {}", documentId, currentUser.getId());
        DocumentDto response = documentService.updateDocument(documentId, file, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Document updated and re-indexed successfully", response));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete document", description = "Removes document metadata and vector embeddings")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable("id") String documentId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to delete document {} for user id: {}", documentId, currentUser.getId());
        documentService.deleteDocument(documentId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Document deleted successfully", null));
    }

    @PostMapping("/sync-knowledge")
    @Operation(summary = "Synchronize website knowledge files", description = "Discovers and indexes all website files and documentation")
    public ResponseEntity<ApiResponse<java.util.Map<String, Object>>> syncKnowledge(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to sync website knowledge by user id: {}", currentUser.getId());
        int count = websiteKnowledgeService.syncWebsiteKnowledge();
        return ResponseEntity.ok(ApiResponse.success("Website knowledge synced successfully", java.util.Map.of("syncedFiles", count)));
    }
}
