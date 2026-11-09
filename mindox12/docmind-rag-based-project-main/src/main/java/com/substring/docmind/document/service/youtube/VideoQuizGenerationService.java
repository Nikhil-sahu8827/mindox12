package com.substring.docmind.document.service.youtube;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import com.substring.docmind.document.dto.youtube.YouTubeLearningMaterialsDto.VideoQuizQuestion;
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
public class VideoQuizGenerationService {

    @Autowired(required = false)
    private ChatModel chatModel;

    private final TranscriptProcessingService processingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<VideoQuizQuestion> generateQuiz(
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
                You are a university examiner creating a test quiz for the video "%s".
                Create %d quiz questions (MCQs and True/False) strictly derived from the spoken video transcript below.

                STRICT ACCURACY RULES:
                1. Every question and answer must be explicitly discussed in the video transcript.
                2. Do not invent facts or test outside knowledge.
                3. Include exact timestamp references (e.g. "[15:30]") for each question.
                4. For MCQs provide exactly 4 options. For True/False provide 2 options: ["True", "False"].
                5. "correctOptionIndex" must be a 0-based integer (0, 1, 2, or 3).
                6. Provide a clear explanation referencing what the speaker said.

                RESPOND ONLY WITH A VALID JSON ARRAY IN THIS EXACT FORMAT:
                [
                  {
                    "question": "What is the primary benefit of lambda expressions discussed in the video?",
                    "options": ["Option A", "Option B", "Option C", "Option D"],
                    "correctOptionIndex": 0,
                    "explanation": "At [05:20], the speaker explicitly demonstrates that...",
                    "questionType": "MCQ",
                    "timestamp": "[05:20]"
                  }
                ]

                TRANSCRIPT EXCERPTS ACROSS VIDEO:
                %s
                """, videoTitle, targetCount, transcriptSample);

        try {
            ChatClient client = ChatClient.create(chatModel);
            String response = client.prompt(prompt).call().content();
            String cleanJson = extractJsonArray(response);
            return objectMapper.readValue(cleanJson, new TypeReference<List<VideoQuizQuestion>>() {});
        } catch (Exception e) {
            log.error("Failed to generate quiz for '{}': {}", videoTitle, e.getMessage());
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
