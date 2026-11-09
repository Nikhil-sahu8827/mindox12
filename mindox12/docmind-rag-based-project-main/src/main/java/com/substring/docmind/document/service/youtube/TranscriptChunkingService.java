package com.substring.docmind.document.service.youtube;

import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TranscriptChunkingService {

    private final TranscriptProcessingService processingService;

    /**
     * Chunks a complete transcript into coherent timestamped chunks (~1200-1500 chars or 3-4 minute windows)
     * without losing a single spoken word or sentence.
     */
    public List<Document> chunkTranscript(
            List<TranscriptSegmentDto> rawSegments,
            String documentId,
            Long userId,
            String videoId,
            String videoTitle,
            String videoUrl
    ) {
        List<TranscriptSegmentDto> segments = processingService.cleanAndNormalizeSegments(rawSegments);
        if (segments.isEmpty()) {
            return Collections.emptyList();
        }

        List<Document> chunks = new ArrayList<>();
        int targetChunkChars = 1400;
        int overlapSegmentsCount = 2; // Keep 2 segments overlap for semantic continuity

        List<TranscriptSegmentDto> currentChunkSegments = new ArrayList<>();
        int currentLength = 0;

        for (int i = 0; i < segments.size(); i++) {
            TranscriptSegmentDto seg = segments.get(i);
            currentChunkSegments.add(seg);
            currentLength += seg.getText().length() + 15; // approximate timestamp overhead

            if (currentLength >= targetChunkChars || i == segments.size() - 1) {
                Document docChunk = buildChunkDocument(
                        currentChunkSegments,
                        chunks.size(),
                        documentId,
                        userId,
                        videoId,
                        videoTitle,
                        videoUrl
                );
                chunks.add(docChunk);

                // Prepare next chunk with slight overlap
                List<TranscriptSegmentDto> nextChunk = new ArrayList<>();
                int overlapStart = Math.max(0, currentChunkSegments.size() - overlapSegmentsCount);
                for (int j = overlapStart; j < currentChunkSegments.size(); j++) {
                    nextChunk.add(currentChunkSegments.get(j));
                }
                currentChunkSegments = nextChunk;
                currentLength = currentChunkSegments.stream().mapToInt(s -> s.getText().length() + 15).sum();
            }
        }

        // Set totalChunks in metadata for every chunk
        int total = chunks.size();
        for (Document d : chunks) {
            d.getMetadata().put("total_chunks", total);
            d.getMetadata().put("totalChunks", total);
        }

        log.info("Chunked {} transcript segments into {} semantic vector chunks for video: {}",
                segments.size(), total, videoId);

        return chunks;
    }

    private Document buildChunkDocument(
            List<TranscriptSegmentDto> chunkSegments,
            int chunkIndex,
            String documentId,
            Long userId,
            String videoId,
            String videoTitle,
            String videoUrl
    ) {
        TranscriptSegmentDto first = chunkSegments.get(0);
        TranscriptSegmentDto last = chunkSegments.get(chunkSegments.size() - 1);

        long startMs = first.getStartMs();
        long endMs = last.getStartMs() + last.getDurationMs();
        String startTimeStr = YouTubeTranscriptService.formatTimeMs(startMs);
        String endTimeStr = YouTubeTranscriptService.formatTimeMs(endMs);
        String timeRange = String.format("[%s - %s]", startTimeStr, endTimeStr);

        StringBuilder textBuilder = new StringBuilder();
        textBuilder.append("VIDEO: ").append(videoTitle).append("\n");
        textBuilder.append("TIMESTAMPS: ").append(timeRange).append("\n");
        textBuilder.append("VIDEO URL: ").append(videoUrl).append("&t=").append(startMs / 1000).append("s\n\n");
        textBuilder.append("TRANSCRIPT CONTENT:\n");

        for (TranscriptSegmentDto s : chunkSegments) {
            textBuilder.append("[").append(s.getTimestamp()).append("] ").append(s.getText()).append("\n");
        }

        Map<String, Object> metadata = new HashMap<>();
        // Standard DocMind camelCase keys (used by QuizService, VectorSearchService, etc.)
        metadata.put("documentId", documentId);
        metadata.put("userId", userId);
        metadata.put("fileName", "[YouTube] " + videoTitle);
        metadata.put("contentType", "video/youtube");
        metadata.put("chunkIndex", chunkIndex);

        // Snake_case keys for YouTube-specific services
        metadata.put("document_id", documentId);
        metadata.put("user_id", userId);
        metadata.put("chunk_index", chunkIndex);
        metadata.put("video_id", videoId);
        metadata.put("video_url", videoUrl);
        metadata.put("start_ms", startMs);
        metadata.put("end_ms", endMs);
        metadata.put("start_time", startTimeStr);
        metadata.put("end_time", endTimeStr);
        metadata.put("time_range", timeRange);
        metadata.put("file_name", "[YouTube] " + videoTitle);
        metadata.put("content_type", "video/youtube");
        metadata.put("source", "youtube_transcript");

        return new Document(textBuilder.toString().trim(), metadata);
    }
}
