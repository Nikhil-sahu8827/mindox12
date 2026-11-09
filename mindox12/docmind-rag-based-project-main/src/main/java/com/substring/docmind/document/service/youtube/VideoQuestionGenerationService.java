package com.substring.docmind.document.service.youtube;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import com.substring.docmind.document.dto.youtube.YouTubeLearningMaterialsDto.VideoQuestion;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class VideoQuestionGenerationService {

    @Autowired(required = false)
    private ChatModel chatModel;

    private final TranscriptProcessingService processingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<VideoQuestion> generateQuestions(
            String videoTitle,
            List<TranscriptSegmentDto> segments,
            int count
    ) {
        if (segments == null || segments.isEmpty() || chatModel == null) {
            return new ArrayList<>();
        }

        List<TranscriptProcessingService.ParagraphBlock> paragraphs = processingService.groupIntoParagraphs(segments);
        StringBuilder transcriptSample = new StringBuilder();
        int step = Math.max(1, paragraphs.size() / 25);
        for (int i = 0; i < paragraphs.size(); i += step) {
            TranscriptProcessingService.ParagraphBlock p = paragraphs.get(i);
            transcriptSample.append(p.timeRange()).append(" ").append(p.content()).append("\n\n");
        }

        int targetCount = Math.max(5, Math.min(count, 15));

        String prompt = String.format("""
                You are a test preparation expert creating study questions for the video "%s".
                Generate %d questions strictly derived from the spoken video transcript below.

                STRICT ACCURACY RULES:
                1. Only ask questions about information explicitly covered in the video transcript.
                2. Do not hallucinate or use external facts not discussed in the video.
                3. Provide the exact video timestamp (e.g. "[08:45]") where the answer is found.
                4. Include a diverse mix of question types:
                   - CONCEPTUAL: tests understanding of "why" or "how"
                   - RECALL: tests key definitions or syntax
                   - CRITICAL_THINKING: tests analysis or edge cases explained by the speaker

                RESPOND ONLY WITH VALID JSON IN THIS FORMAT:
                [
                  {
                    "question": "What is...",
                    "answer": "The speaker explains that...",
                    "timestamp": "[04:20]",
                    "questionType": "CONCEPTUAL"
                  }
                ]

                TRANSCRIPT EXCERPTS:
                %s
                """, videoTitle, targetCount, transcriptSample);

        try {
            ChatClient client = ChatClient.create(chatModel);
            String response = client.prompt(prompt).call().content();
            String cleanJson = extractJsonArray(response);
            return objectMapper.readValue(cleanJson, new TypeReference<List<VideoQuestion>>() {});
        } catch (Exception e) {
            log.error("Failed to generate questions for '{}': {}", videoTitle, e.getMessage());
            return new ArrayList<>();
        }
    }

    private String extractJsonArray(String text) {
        if (text == null) return "[]";
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return "[]";
    }
}
