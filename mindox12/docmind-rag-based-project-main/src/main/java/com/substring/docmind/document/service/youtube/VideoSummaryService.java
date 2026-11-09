package com.substring.docmind.document.service.youtube;

import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoSummaryService {

    @Autowired(required = false)
    private ChatModel chatModel;

    private final TranscriptProcessingService processingService;

    public String generateCompleteSummary(
            String videoTitle,
            String videoAuthor,
            List<TranscriptSegmentDto> segments,
            String overviewDescription
    ) {
        if (segments == null || segments.isEmpty()) {
            return "No transcript is available for this video to generate a summary.";
        }

        if (chatModel == null) {
            return "AI model is not currently configured for summary generation.";
        }

        // Build condensed timestamped transcript representation
        List<TranscriptProcessingService.ParagraphBlock> paragraphs = processingService.groupIntoParagraphs(segments);
        StringBuilder transcriptSample = new StringBuilder();

        // Sample evenly across the video so the entire timeline is covered from beginning to end
        int step = Math.max(1, paragraphs.size() / 35);
        for (int i = 0; i < paragraphs.size(); i += step) {
            TranscriptProcessingService.ParagraphBlock p = paragraphs.get(i);
            transcriptSample.append(p.timeRange()).append(" ").append(p.content()).append("\n\n");
        }

        String prompt = String.format("""
                You are an elite academic professor and curriculum summarizer.
                Your task is to generate a comprehensive, highly structured executive summary of the educational video:
                Title: "%s"
                Channel: "%s"

                STRICT ACCURACY RULES:
                1. Only use facts, topics, definitions, and explanations explicitly stated in the provided transcript below.
                2. Do not invent topics, definitions, or statements that are not supported by the video.
                3. Include exact timestamp citations (e.g. [00:12:30]) where key topics, rules, and milestones are taught.
                4. Structure the summary with:
                   - 📌 Executive Overview
                   - ⏱️ Chronological Key Topics & Sections (with timestamps)
                   - 💡 Core Concepts & Practical Takeaways
                   - 🏁 Summary Conclusion

                TRANSCRIPT EXCERPTS ACROSS THE COMPLETE VIDEO TIMELINE:
                %s
                """, videoTitle, videoAuthor, transcriptSample);

        try {
            ChatClient client = ChatClient.create(chatModel);
            return client.prompt(prompt).call().content();
        } catch (Exception e) {
            log.error("Failed to generate video summary for '{}': {}", videoTitle, e.getMessage());
            return "Error generating AI summary: " + e.getMessage();
        }
    }
}
