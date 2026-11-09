package com.substring.docmind.document.service;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.enums.AuthProvider;
import com.substring.docmind.enums.DocumentStatus;
import com.substring.docmind.enums.Role;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class WebsiteKnowledgeService {

    private final DocumentParserService parserService;
    private final DocumentIngestionService ingestionService;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UserRepository userRepository;

    private static final String SYSTEM_USERNAME = "system";
    private static final String SYSTEM_DOC_PREFIX = "sys_kb_";

    @EventListener(ApplicationReadyEvent.class)
    public void onStartup() {
        log.info("Triggering automatic website knowledge file synchronization...");
        try {
            syncWebsiteKnowledge();
        } catch (Exception e) {
            log.error("Failed initial website knowledge synchronization: {}", e.getMessage(), e);
        }
    }

    @Transactional
    public int syncWebsiteKnowledge() {
        log.info("Starting website-wide file discovery and knowledge indexing...");
        User systemUser = getOrCreateSystemUser();

        List<File> filesToIndex = discoverWebsiteFiles();
        log.info("Discovered {} candidate website files for knowledge base", filesToIndex.size());

        int indexedCount = 0;
        for (File file : filesToIndex) {
            try {
                if (indexOrUpdateFile(file, systemUser)) {
                    indexedCount++;
                }
            } catch (Exception e) {
                log.error("Failed indexing website file {}: {}", file.getName(), e.getMessage());
            }
        }

        log.info("Completed website knowledge synchronization. Total indexed/verified: {}", indexedCount);
        return indexedCount;
    }

    private List<File> discoverWebsiteFiles() {
        List<File> files = new ArrayList<>();

        // 1. Dedicated website_knowledge directory under data/
        Path kbDir = Paths.get("data", "website_knowledge");
        try {
            if (!Files.exists(kbDir)) {
                Files.createDirectories(kbDir);
            }
            File[] kbFiles = kbDir.toFile().listFiles((dir, name) -> isSupportedExtension(name));
            if (kbFiles != null) {
                files.addAll(Arrays.asList(kbFiles));
            }
        } catch (Exception e) {
            log.warn("Could not check website_knowledge folder: {}", e.getMessage());
        }

        // 2. Project root reference documents
        List<String> rootDocs = List.of(
                "Biology_Sample.txt",
                "Cloud_Computing.txt"
        );
        for (String docName : rootDocs) {
            File f = new File(docName);
            if (f.exists() && f.isFile() && f.length() > 0) {
                files.add(f);
            }
        }

        // 3. Documentation directory
        File docsDir = new File("docs");
        if (docsDir.exists() && docsDir.isDirectory()) {
            File[] docFiles = docsDir.listFiles((dir, name) -> name.endsWith(".md") || name.endsWith(".txt"));
            if (docFiles != null) {
                files.addAll(Arrays.asList(docFiles));
            }
        }

        return files;
    }

    private boolean isSupportedExtension(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".pdf") || n.endsWith(".docx") || n.endsWith(".csv") ||
               n.endsWith(".txt") || n.endsWith(".md") || n.endsWith(".html") ||
               n.endsWith(".json") || n.endsWith(".xml");
    }

    private boolean indexOrUpdateFile(File file, User systemUser) throws Exception {
        String fileName = file.getName();
        String stableDocId = SYSTEM_DOC_PREFIX + generateDeterministicId(file.getAbsolutePath());
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length == 0) {
            return false;
        }

        String currentHash = calculateHash(bytes);

        Optional<DocumentMetadata> existingOpt = documentMetadataRepository.findById(stableDocId);
        if (existingOpt.isPresent()) {
            DocumentMetadata existing = existingOpt.get();
            // Check if file content hasn't changed (stored in sourceUrl field as sha256:hash)
            if (existing.getSourceUrl() != null && existing.getSourceUrl().equals("sha256:" + currentHash)
                    && existing.getStatus() == DocumentStatus.INDEXED) {
                log.debug("Website file {} is already indexed and unchanged (hash: {})", fileName, currentHash);
                return true;
            }
            log.info("Website file {} modified or incomplete. Re-indexing...", fileName);
            ingestionService.deleteDocumentVectors(stableDocId);
        }

        log.info("Indexing website knowledge document: {} ({} bytes)", fileName, bytes.length);

        int pageCount = parserService.extractPageCount(bytes, fileName);
        String text = parserService.extractText(bytes, fileName);
        if (text.isBlank()) {
            log.warn("No text extracted from website file: {}", fileName);
            return false;
        }

        String contentType = determineContentType(fileName);
        List<Document> chunks = parserService.chunkText(
                text, stableDocId, systemUser.getId(), fileName, contentType, pageCount, true
        );

        DocumentMetadata metadata = existingOpt.orElseGet(() -> DocumentMetadata.builder()
                .id(stableDocId)
                .user(systemUser)
                .filename(fileName)
                .build());

        metadata.setUser(systemUser);
        metadata.setFilename(fileName);
        metadata.setContentType(contentType);
        metadata.setFileSize(file.length());
        metadata.setStatus(DocumentStatus.PROCESSING);
        metadata.setTotalPages(pageCount);
        metadata.setTotalChunks(chunks.size());
        metadata.setSourceUrl("sha256:" + currentHash);

        DocumentMetadata saved = documentMetadataRepository.saveAndFlush(metadata);

        if (!chunks.isEmpty()) {
            ingestionService.ingestChunksSync(stableDocId, chunks);
            saved.setStatus(DocumentStatus.INDEXED);
            saved.setTotalChunks(chunks.size());
            documentMetadataRepository.saveAndFlush(saved);
        }

        log.info("Successfully indexed website knowledge file: {} (chunks: {})", fileName, chunks.size());
        return true;
    }

    private User getOrCreateSystemUser() {
        return userRepository.findByUsername(SYSTEM_USERNAME).orElseGet(() -> {
            log.info("Creating system user for website-wide knowledge ownership");
            User user = User.builder()
                    .username(SYSTEM_USERNAME)
                    .email("system@docmind.internal")
                    .password(UUID.randomUUID().toString())
                    .role(Role.ROLE_ADMIN)
                    .provider(AuthProvider.LOCAL)
                    .enabled(true)
                    .build();
            return userRepository.saveAndFlush(user);
        });
    }

    private String calculateHash(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return UUID.randomUUID().toString();
        }
    }

    private String generateDeterministicId(String path) {
        return UUID.nameUUIDFromBytes(path.getBytes()).toString();
    }

    private String determineContentType(String fileName) {
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        if (lower.endsWith(".csv")) return "text/csv";
        if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html";
        if (lower.endsWith(".json")) return "application/json";
        if (lower.endsWith(".md")) return "text/markdown";
        return "text/plain";
    }
}
