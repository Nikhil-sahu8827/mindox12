package com.substring.docmind.document.service.youtube;

import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class TranscriptProcessingService {

    public List<TranscriptSegmentDto> cleanAndNormalizeSegments(List<TranscriptSegmentDto> rawSegments) {
        if (rawSegments == null || rawSegments.isEmpty()) return new ArrayList<>();

        List<TranscriptSegmentDto> cleaned = new ArrayList<>();
        for (TranscriptSegmentDto seg : rawSegments) {
            String text = cleanSegmentText(seg.getText());
            if (!text.isBlank()) {
                cleaned.add(TranscriptSegmentDto.builder()
                        .startMs(seg.getStartMs())
                        .durationMs(seg.getDurationMs())
                        .timestamp(seg.getTimestamp())
                        .text(text)
                        .lang(seg.getLang())
                        .build());
            }
        }
        return cleaned;
    }

    public String cleanSegmentText(String text) {
        if (text == null) return "";
        return text
                .replaceAll("(?i)\\[(?:music|applause|laughter|screaming|cheering|background music)\\]", "")
                .replaceAll("(?i)\\((?:music|applause|laughter)\\)", "")
                .replaceAll("♪+", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Groups consecutive segments into readable multi-sentence paragraphs (~30-60 second intervals)
     * with clear timestamp anchors for students and LLM comprehension.
     */
    public List<ParagraphBlock> groupIntoParagraphs(List<TranscriptSegmentDto> segments) {
        List<ParagraphBlock> paragraphs = new ArrayList<>();
        if (segments == null || segments.isEmpty()) return paragraphs;

        List<TranscriptSegmentDto> currentBatch = new ArrayList<>();
        long batchStartTime = segments.get(0).getStartMs();
        long targetDurationMs = 45000; // ~45 seconds per paragraph

        for (TranscriptSegmentDto seg : segments) {
            currentBatch.add(seg);
            if (seg.getStartMs() - batchStartTime >= targetDurationMs || currentBatch.size() >= 12) {
                paragraphs.add(buildParagraph(currentBatch));
                currentBatch = new ArrayList<>();
                batchStartTime = seg.getStartMs() + seg.getDurationMs();
            }
        }

        if (!currentBatch.isEmpty()) {
            paragraphs.add(buildParagraph(currentBatch));
        }

        return paragraphs;
    }

    private ParagraphBlock buildParagraph(List<TranscriptSegmentDto> batch) {
        long startMs = batch.get(0).getStartMs();
        long endMs = batch.get(batch.size() - 1).getStartMs() + batch.get(batch.size() - 1).getDurationMs();
        String timeRange = String.format("[%s - %s]", YouTubeTranscriptService.formatTimeMs(startMs), YouTubeTranscriptService.formatTimeMs(endMs));

        StringBuilder sb = new StringBuilder();
        for (TranscriptSegmentDto s : batch) {
            sb.append(s.getText()).append(" ");
        }

        return new ParagraphBlock(startMs, endMs, timeRange, sb.toString().trim());
    }

    public record ParagraphBlock(long startMs, long endMs, String timeRange, String content) {}
}
