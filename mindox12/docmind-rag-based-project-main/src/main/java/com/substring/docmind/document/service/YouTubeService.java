package com.substring.docmind.document.service;

import com.substring.docmind.document.dto.DocumentDto;
import com.substring.docmind.document.dto.youtube.*;
import com.substring.docmind.document.dto.youtube.YouTubeChatDto.*;
import com.substring.docmind.document.dto.youtube.YouTubeLearningMaterialsDto.*;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.mapper.DocumentMapper;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.document.service.youtube.*;
import com.substring.docmind.enums.DocumentStatus;
import com.substring.docmind.exception.BadRequestException;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class YouTubeService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final UserRepository userRepository;
    private final DocumentIngestionService ingestionService;
    private final DocumentMapper documentMapper;

    // Modular Backend Services
    private final YouTubeUrlService urlService;
    private final YouTubeMetadataService metadataService;
    private final YouTubeTranscriptService transcriptService;
    private final TranscriptProcessingService processingService;
    private final TranscriptChunkingService chunkingService;
    private final VideoSummaryService summaryService;
    private final VideoQuestionGenerationService questionService;
    private final VideoQuizGenerationService quizService;
    private final VideoNotesService notesService;
    private final VideoChatService chatService;

    // In-memory cache for rich YouTube video data & materials
    private final Map<String, YouTubeVideoDataDto> videoDataCache = new ConcurrentHashMap<>();
    private final Map<String, YouTubeLearningMaterialsDto> learningMaterialsCache = new ConcurrentHashMap<>();

    @Autowired(required = false)
    private com.substring.docmind.growth.service.TopicExtractionService topicExtractionService;

    public boolean isYouTubeUrl(String url) {
        return urlService.isYouTubeUrl(url);
    }

    public String extractVideoId(String url) {
        return urlService.extractVideoId(url);
    }

    /**
     * Complete video ingestion and processing pipeline.
     */
    @Transactional
    public DocumentDto ingestYouTubeVideo(String rawUrl, UserPrincipal currentUser) {
        YouTubeVideoDataDto videoData = processYouTubeUrl(rawUrl, null, currentUser);

        // Fetch document metadata to return DTO
        DocumentMetadata metadata = documentMetadataRepository.findById(videoData.getDocumentId())
                .orElseThrow(() -> new ResourceNotFoundException("DocumentMetadata", "id", videoData.getDocumentId()));

        return documentMapper.toDto(metadata);
    }

    /**
     * Processes YouTube URL completely: extracts full metadata, full transcript, chunks, embeds,
     * and generates initial AI study materials.
     */
    @Transactional
    public YouTubeVideoDataDto processYouTubeUrl(String rawUrl, String preferredLang, UserPrincipal currentUser) {
        String videoId = urlService.extractVideoId(rawUrl);
        if (videoId == null) {
            throw new BadRequestException("Invalid YouTube URL. Please provide a valid YouTube video link (e.g. https://www.youtube.com/watch?v=... or https://youtu.be/...)");
        }

        User user = getUser(currentUser.getId());
        String canonicalUrl = urlService.buildCanonicalUrl(videoId);

        // 1. Fetch complete metadata
        log.info("Fetching complete metadata for YouTube video: {} (user: {})", videoId, user.getId());
        YouTubeMetadataService.VideoMetadata meta = metadataService.fetchMetadata(videoId);

        // 2. Check if already indexed for this user
        Optional<DocumentMetadata> existingOpt = documentMetadataRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(d -> canonicalUrl.equalsIgnoreCase(d.getSourceUrl()) || (d.getSourceUrl() != null && d.getSourceUrl().contains(videoId)))
                .findFirst();

        String documentId = existingOpt.map(DocumentMetadata::getId).orElse(UUID.randomUUID().toString());

        // 3. Extract complete transcript
        log.info("Extracting complete transcript from beginning to end for video: {}", videoId);
        YouTubeTranscriptService.ExtractionResult transcriptResult = transcriptService.extractTranscript(videoId, preferredLang);

        List<TranscriptSegmentDto> segments = transcriptResult.isSuccess() ? transcriptResult.getSegments() : new ArrayList<>();
        segments = processingService.cleanAndNormalizeSegments(segments);

        boolean hasCaptions = !segments.isEmpty();
        String status = hasCaptions ? "READY" : "NO_CAPTIONS";
        String statusMessage = hasCaptions
                ? String.format("Complete transcript successfully extracted (%d segments, duration: %s)", segments.size(), meta.getFormattedDuration())
                : "No captions or transcript tracks available for this video. Study materials will be based on video metadata and overview.";

        // 4. Generate timestamped chunks for vector indexing
        List<Document> chunks = new ArrayList<>();
        if (hasCaptions) {
            chunks = chunkingService.chunkTranscript(
                    segments,
                    documentId,
                    user.getId(),
                    videoId,
                    meta.getTitle(),
                    canonicalUrl
            );
        } else {
            // Fallback chunk with video metadata
            String fallbackContent = String.format("""
                    YOUTUBE VIDEO: %s
                    CREATOR / CHANNEL: %s
                    URL: %s
                    DURATION: %s
                    DESCRIPTION & OVERVIEW:
                    %s
                    """, meta.getTitle(), meta.getAuthor(), canonicalUrl, meta.getFormattedDuration(), meta.getDescription());

            Map<String, Object> fallbackMeta = new HashMap<>();
            fallbackMeta.put("documentId", documentId);
            fallbackMeta.put("document_id", documentId);
            fallbackMeta.put("userId", user.getId());
            fallbackMeta.put("user_id", user.getId());
            fallbackMeta.put("fileName", "[YouTube] " + meta.getTitle());
            fallbackMeta.put("file_name", "[YouTube] " + meta.getTitle());
            fallbackMeta.put("contentType", "video/youtube");
            fallbackMeta.put("content_type", "video/youtube");
            fallbackMeta.put("chunkIndex", 0);
            fallbackMeta.put("chunk_index", 0);
            fallbackMeta.put("totalChunks", 1);
            fallbackMeta.put("total_chunks", 1);
            fallbackMeta.put("video_id", videoId);
            fallbackMeta.put("video_url", canonicalUrl);
            fallbackMeta.put("source", "youtube_metadata");
            chunks.add(new Document(fallbackContent, fallbackMeta));
        }

        // 5. Save or update DocumentMetadata
        String cleanTitle = meta.getTitle().replaceAll("[^a-zA-Z0-9\\s._-]", "").trim();
        if (cleanTitle.length() > 80) cleanTitle = cleanTitle.substring(0, 80).trim();
        String filename = "[YouTube] " + (cleanTitle.isBlank() ? videoId : cleanTitle);

        long contentBytes = hasCaptions && transcriptResult.getFullTranscriptText() != null
                ? transcriptResult.getFullTranscriptText().getBytes(StandardCharsets.UTF_8).length
                : meta.getDescription().getBytes(StandardCharsets.UTF_8).length + 200;

        DocumentMetadata metadata = DocumentMetadata.builder()
                .id(documentId)
                .user(user)
                .filename(filename)
                .contentType("video/youtube")
                .sourceUrl(canonicalUrl)
                .fileSize(contentBytes)
                .status(DocumentStatus.INDEXED)
                .totalPages(1)
                .totalChunks(chunks.size())
                .build();

        documentMetadataRepository.saveAndFlush(metadata);

        // 6. Ingest vector chunks
        if (!chunks.isEmpty()) {
            try {
                ingestionService.ingestChunksSync(documentId, chunks);
            } catch (Exception e) {
                log.warn("Ingest chunks notice for YouTube video {}: {}", videoId, e.getMessage());
            }
        }

        if (topicExtractionService != null) {
            try {
                topicExtractionService.extractTopicsForDocument(metadata, user);
            } catch (Exception e) {
                log.warn("Automatic topic extraction for YouTube video {}: {}", videoId, e.getMessage());
            }
        }

        // 7. Assemble rich YouTubeVideoDataDto
        List<TranscriptSegmentDto> preview = segments.size() > 50
                ? segments.subList(0, 50)
                : segments;

        YouTubeVideoDataDto videoData = YouTubeVideoDataDto.builder()
                .documentId(documentId)
                .videoId(videoId)
                .canonicalUrl(canonicalUrl)
                .title(meta.getTitle())
                .author(meta.getAuthor())
                .description(meta.getDescription())
                .durationSeconds(meta.getDurationSeconds())
                .formattedDuration(meta.getFormattedDuration())
                .thumbnailUrl(meta.getThumbnailUrl())
                .viewCount(meta.getViewCount())
                .status(status)
                .statusMessage(statusMessage)
                .captionLanguage(transcriptResult.getCaptionLanguage())
                .isAutoGenerated(transcriptResult.isAutoGenerated())
                .totalSegments(segments.size())
                .totalChunks(chunks.size())
                .transcriptPreview(preview)
                .fullTranscriptText(transcriptResult.getFullTranscriptText())
                .keywords(meta.getKeywords())
                .aiArtifacts(new HashMap<>())
                .build();

        videoDataCache.put(videoId, videoData);
        videoDataCache.put(documentId, videoData);

        log.info("Successfully processed YouTube video '{}' ({} segments, {} chunks) as doc ID {}",
                meta.getTitle(), segments.size(), chunks.size(), documentId);

        return videoData;
    }

    /**
     * Retrieve video data by videoId or documentId
     */
    public YouTubeVideoDataDto getVideoData(String videoIdOrDocId, UserPrincipal currentUser) {
        if (videoIdOrDocId == null || videoIdOrDocId.isBlank()) {
            throw new BadRequestException("Video ID or Document ID is required");
        }

        String key = videoIdOrDocId.trim();
        if (videoDataCache.containsKey(key)) {
            return videoDataCache.get(key);
        }

        // Try finding by document ID in database
        Optional<DocumentMetadata> docOpt = documentMetadataRepository.findById(key);
        if (docOpt.isPresent() && "video/youtube".equalsIgnoreCase(docOpt.get().getContentType())) {
            DocumentMetadata doc = docOpt.get();
            String videoId = urlService.extractVideoId(doc.getSourceUrl());
            if (videoId != null) {
                return processYouTubeUrl(doc.getSourceUrl(), null, currentUser);
            }
        }

        // Try extracting as video ID
        String vid = urlService.extractVideoId(key);
        if (vid != null) {
            return processYouTubeUrl(urlService.buildCanonicalUrl(vid), null, currentUser);
        }

        throw new ResourceNotFoundException("YouTube Video", "id", key);
    }

    /**
     * Generate or fetch full video summary
     */
    public String getVideoSummary(String videoIdOrDocId, UserPrincipal currentUser) {
        YouTubeVideoDataDto videoData = getVideoData(videoIdOrDocId, currentUser);
        YouTubeLearningMaterialsDto materials = getOrCreateMaterials(videoData);

        if (materials.getSummary() != null && !materials.getSummary().isBlank()) {
            return materials.getSummary();
        }

        List<TranscriptSegmentDto> segments = getSegmentsForVideo(videoData.getVideoId());
        String summary = summaryService.generateCompleteSummary(
                videoData.getTitle(),
                videoData.getAuthor(),
                segments,
                videoData.getDescription()
        );

        materials.setSummary(summary);
        learningMaterialsCache.put(videoData.getVideoId(), materials);
        videoData.getAiArtifacts().put("summary", summary);

        return summary;
    }

    /**
     * Generate or fetch video questions
     */
    public List<VideoQuestion> getVideoQuestions(String videoIdOrDocId, int count, UserPrincipal currentUser) {
        YouTubeVideoDataDto videoData = getVideoData(videoIdOrDocId, currentUser);
        YouTubeLearningMaterialsDto materials = getOrCreateMaterials(videoData);

        if (materials.getQuestions() != null && !materials.getQuestions().isEmpty()) {
            return materials.getQuestions();
        }

        List<TranscriptSegmentDto> segments = getSegmentsForVideo(videoData.getVideoId());
        List<VideoQuestion> questions = questionService.generateQuestions(videoData.getTitle(), segments, count);

        materials.setQuestions(questions);
        learningMaterialsCache.put(videoData.getVideoId(), materials);
        videoData.getAiArtifacts().put("questions", questions);

        return questions;
    }

    /**
     * Generate or fetch video quiz questions
     */
    public List<VideoQuizQuestion> getVideoQuiz(String videoIdOrDocId, int count, UserPrincipal currentUser) {
        YouTubeVideoDataDto videoData = getVideoData(videoIdOrDocId, currentUser);
        YouTubeLearningMaterialsDto materials = getOrCreateMaterials(videoData);

        if (materials.getQuizQuestions() != null && !materials.getQuizQuestions().isEmpty()) {
            return materials.getQuizQuestions();
        }

        List<TranscriptSegmentDto> segments = getSegmentsForVideo(videoData.getVideoId());
        List<VideoQuizQuestion> quiz = quizService.generateQuiz(videoData.getTitle(), segments, count);

        materials.setQuizQuestions(quiz);
        learningMaterialsCache.put(videoData.getVideoId(), materials);
        videoData.getAiArtifacts().put("quiz", quiz);

        return quiz;
    }

    /**
     * Generate or fetch detailed notes, key concepts, and flashcards
     */
    public YouTubeLearningMaterialsDto getVideoNotes(String videoIdOrDocId, UserPrincipal currentUser) {
        YouTubeVideoDataDto videoData = getVideoData(videoIdOrDocId, currentUser);
        YouTubeLearningMaterialsDto materials = getOrCreateMaterials(videoData);

        if (materials.getTopicNotes() != null && !materials.getTopicNotes().isEmpty()) {
            return materials;
        }

        List<TranscriptSegmentDto> segments = getSegmentsForVideo(videoData.getVideoId());
        VideoNotesService.VideoNotesResult res = notesService.generateNotesAndMaterials(
                videoData.getTitle(),
                videoData.getAuthor(),
                segments
        );

        materials.setTopicNotes(res.topicNotes());
        materials.setImportantTopics(res.importantTopics());
        materials.setTechnicalTerms(res.technicalTerms());
        materials.setConceptExplanations(res.conceptExplanations());
        materials.setFlashcards(res.flashcards());

        learningMaterialsCache.put(videoData.getVideoId(), materials);
        videoData.getAiArtifacts().put("notes", materials);

        return materials;
    }

    /**
     * Interactive Video Chatbot
     */
    public Response chatWithVideo(Request request, UserPrincipal currentUser) {
        String key = request.getVideoId() != null && !request.getVideoId().isBlank()
                ? request.getVideoId()
                : request.getDocumentId();

        YouTubeVideoDataDto videoData = getVideoData(key, currentUser);
        List<TranscriptSegmentDto> segments = getSegmentsForVideo(videoData.getVideoId());

        return chatService.chatWithVideo(request.getQuestion(), videoData.getTitle(), segments);
    }

    private YouTubeLearningMaterialsDto getOrCreateMaterials(YouTubeVideoDataDto videoData) {
        return learningMaterialsCache.computeIfAbsent(videoData.getVideoId(), k ->
                YouTubeLearningMaterialsDto.builder()
                        .videoId(videoData.getVideoId())
                        .title(videoData.getTitle())
                        .build());
    }

    private List<TranscriptSegmentDto> getSegmentsForVideo(String videoId) {
        YouTubeVideoDataDto data = videoDataCache.get(videoId);
        if (data != null && data.getTranscriptPreview() != null && !data.getTranscriptPreview().isEmpty()) {
            // If full segments available in cache, return them
            if (data.getTotalSegments() == data.getTranscriptPreview().size()) {
                return data.getTranscriptPreview();
            }
        }
        // Extract directly
        YouTubeTranscriptService.ExtractionResult res = transcriptService.extractTranscript(videoId, null);
        return res.isSuccess() ? res.getSegments() : new ArrayList<>();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
