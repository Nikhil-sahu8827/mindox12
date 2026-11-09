package com.substring.docmind.document.service.youtube;

import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import com.substring.docmind.document.dto.youtube.YouTubeChatDto.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoChatService {

    @Autowired(required = false)
    private ChatModel chatModel;

    private static final String NOT_AVAILABLE_MSG = "This information is not available in the provided video.";

    public Response chatWithVideo(
            String question,
            String videoTitle,
            List<TranscriptSegmentDto> allSegments
    ) {
        if (question == null || question.isBlank()) {
            return Response.builder()
                    .answer("Please ask a question about the video content.")
                    .foundInVideo(false)
                    .timestamps(new ArrayList<>())
                    .relevantSegments(new ArrayList<>())
                    .build();
        }

        if (allSegments == null || allSegments.isEmpty()) {
            return Response.builder()
                    .answer("No transcript is available for this video.")
                    .foundInVideo(false)
                    .timestamps(new ArrayList<>())
                    .relevantSegments(new ArrayList<>())
                    .build();
        }

        if (chatModel == null) {
            return Response.builder()
                    .answer("AI service is currently unavailable.")
                    .foundInVideo(false)
                    .timestamps(new ArrayList<>())
                    .relevantSegments(new ArrayList<>())
                    .build();
        }

        // 1. Select context segments: if video is short (<= 80 segments), include complete transcript!
        List<TranscriptSegmentDto> contextSegments;
        if (allSegments.size() <= 80) {
            contextSegments = allSegments;
        } else {
            List<TranscriptSegmentDto> keywordMatches = findRelevantSegments(question, allSegments, 30);
            List<TranscriptSegmentDto> timelineSamples = sampleTimeline(allSegments, 20);
            Set<TranscriptSegmentDto> combined = new LinkedHashSet<>(keywordMatches);
            combined.addAll(timelineSamples);
            contextSegments = new ArrayList<>(combined);
            contextSegments.sort(Comparator.comparingLong(TranscriptSegmentDto::getStartMs));
        }

        StringBuilder contextBuilder = new StringBuilder();
        for (TranscriptSegmentDto s : contextSegments) {
            contextBuilder.append("[").append(s.getTimestamp()).append("] ").append(s.getText()).append("\n");
        }

        String prompt = String.format("""
                You are a helpful, strictly accurate AI tutor answering questions about the video:
                Title: "%s"

                STRICT ACCURACY RULES:
                1. Answer the user's question using the factual statements in the video transcript below.
                2. If the user asks about something completely absent and unrelated to what is discussed in the video, you MUST respond exactly:
                   "%s"
                3. If the question refers to entities, terms, or concepts discussed in the video (e.g. asking about animals when elephants are discussed), answer based on what the speaker says.
                4. Always cite the exact video timestamp (e.g. [00:01]) where the information is stated.
                5. Do NOT invent definitions, topics, or outside facts that are not supported by the video.

                USER QUESTION:
                %s

                VIDEO TRANSCRIPT:
                %s
                """, videoTitle, NOT_AVAILABLE_MSG, question, contextBuilder);

        try {
            ChatClient client = ChatClient.create(chatModel);
            String answer = client.prompt(prompt).call().content();

            boolean foundInVideo = answer != null && !answer.contains(NOT_AVAILABLE_MSG);
            List<String> timestamps = extractTimestamps(answer);

            return Response.builder()
                    .answer(answer)
                    .foundInVideo(foundInVideo)
                    .timestamps(timestamps)
                    .relevantSegments(contextSegments.subList(0, Math.min(5, contextSegments.size())))
                    .build();

        } catch (Exception e) {
            log.error("Error during video chat for '{}': {}", videoTitle, e.getMessage());
            return Response.builder()
                    .answer("An error occurred while analyzing the video: " + e.getMessage())
                    .foundInVideo(false)
                    .timestamps(new ArrayList<>())
                    .relevantSegments(new ArrayList<>())
                    .build();
        }
    }

    private List<TranscriptSegmentDto> findRelevantSegments(String query, List<TranscriptSegmentDto> all, int maxResults) {
        String[] keywords = query.toLowerCase()
                .replaceAll("[^a-zA-Z0-9\\s]", " ")
                .split("\\s+");

        Set<String> stopWords = Set.of("what", "is", "the", "in", "video", "how", "does", "explain", "why", "are", "about", "tell", "me");
        List<String> validKeywords = new ArrayList<>();
        for (String k : keywords) {
            if (k.length() >= 3 && !stopWords.contains(k)) {
                validKeywords.add(k);
            }
        }

        if (validKeywords.isEmpty()) {
            return Collections.emptyList();
        }

        // Score segments by keyword frequency
        record ScoredSegment(TranscriptSegmentDto segment, int score) {}
        List<ScoredSegment> scored = new ArrayList<>();

        for (int i = 0; i < all.size(); i++) {
            TranscriptSegmentDto seg = all.get(i);
            String textLower = seg.getText().toLowerCase();
            int score = 0;
            for (String kw : validKeywords) {
                if (textLower.contains(kw)) {
                    score += 2;
                }
            }
            if (score > 0) {
                scored.add(new ScoredSegment(seg, score));
            }
        }

        scored.sort((a, b) -> Integer.compare(b.score, a.score));

        List<TranscriptSegmentDto> result = new ArrayList<>();
        for (int i = 0; i < Math.min(maxResults, scored.size()); i++) {
            result.add(scored.get(i).segment);
        }

        // Sort selected segments chronologically
        result.sort(Comparator.comparingLong(TranscriptSegmentDto::getStartMs));
        return result;
    }

    private List<TranscriptSegmentDto> sampleTimeline(List<TranscriptSegmentDto> all, int count) {
        List<TranscriptSegmentDto> result = new ArrayList<>();
        if (all.isEmpty()) return result;
        int step = Math.max(1, all.size() / count);
        for (int i = 0; i < all.size(); i += step) {
            result.add(all.get(i));
        }
        return result;
    }

    private List<String> extractTimestamps(String text) {
        List<String> list = new ArrayList<>();
        if (text == null) return list;
        Pattern p = Pattern.compile("\\[(\\d{1,2}:\\d{2}(?::\\d{2})?)\\]");
        Matcher m = p.matcher(text);
        while (m.find()) {
            list.add(m.group(1));
        }
        return list;
    }
}
