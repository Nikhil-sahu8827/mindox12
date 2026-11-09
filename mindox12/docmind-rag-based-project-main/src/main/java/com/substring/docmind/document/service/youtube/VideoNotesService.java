package com.substring.docmind.document.service.youtube;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.docmind.document.dto.youtube.TranscriptSegmentDto;
import com.substring.docmind.document.dto.youtube.YouTubeLearningMaterialsDto.*;
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
public class VideoNotesService {

    @Autowired(required = false)
    private ChatModel chatModel;

    private final TranscriptProcessingService processingService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public VideoNotesResult generateNotesAndMaterials(
            String videoTitle,
            String author,
            List<TranscriptSegmentDto> segments
    ) {
        if (segments == null || segments.isEmpty() || chatModel == null) {
            return new VideoNotesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }

        List<TranscriptProcessingService.ParagraphBlock> paragraphs = processingService.groupIntoParagraphs(segments);
        StringBuilder transcriptSample = new StringBuilder();
        int step = Math.max(1, paragraphs.size() / 30);
        for (int i = 0; i < paragraphs.size(); i += step) {
            TranscriptProcessingService.ParagraphBlock p = paragraphs.get(i);
            transcriptSample.append(p.timeRange()).append(" ").append(p.content()).append("\n\n");
        }

        String prompt = String.format("""
                You are a master academic researcher and educator preparing lecture study materials for the video:
                Title: "%s"
                Speaker: "%s"

                STRICT ACCURACY RULES:
                1. Only extract and explain topics, definitions, and concepts directly discussed in the video transcript below.
                2. Do not invent facts, definitions, or statements.
                3. Include exact video timestamps (e.g. "[10:15]") for all notes, concepts, and flashcards.

                RESPOND ONLY WITH VALID JSON IN THIS EXACT FORMAT:
                {
                  "topicNotes": [
                    {
                      "topic": "Topic Name",
                      "timestamp": "[08:15]",
                      "definition": "Precise definition given in video",
                      "detailedExplanation": "How the speaker breaks it down...",
                      "example": "Code/real-world example shown in video"
                    }
                  ],
                  "importantTopics": ["Topic 1", "Topic 2", "Topic 3"],
                  "technicalTerms": ["Term 1", "Term 2", "Term 3"],
                  "conceptExplanations": [
                    {
                      "concept": "Difficult Concept Name",
                      "timestamp": "[14:20]",
                      "simpleLanguageExplanation": "Plain English beginner-friendly breakdown...",
                      "analogy": "Helpful analogy..."
                    }
                  ],
                  "flashcards": [
                    {
                      "front": "Question or Key Term",
                      "back": "Answer or Core Definition",
                      "topic": "Topic Name",
                      "timestamp": "[14:20]"
                    }
                  ]
                }

                TRANSCRIPT EXCERPTS:
                %s
                """, videoTitle, author, transcriptSample);

        try {
            ChatClient client = ChatClient.create(chatModel);
            String response = client.prompt(prompt).call().content();
            String cleanJson = extractJsonObject(response);
            JsonNode root = objectMapper.readTree(cleanJson);

            List<TopicNote> notes = new ArrayList<>();
            if (root.has("topicNotes") && root.get("topicNotes").isArray()) {
                for (JsonNode n : root.get("topicNotes")) {
                    notes.add(TopicNote.builder()
                            .topic(n.path("topic").asText())
                            .timestamp(n.path("timestamp").asText())
                            .definition(n.path("definition").asText())
                            .detailedExplanation(n.path("detailedExplanation").asText())
                            .example(n.path("example").asText())
                            .build());
                }
            }

            List<String> importantTopics = new ArrayList<>();
            if (root.has("importantTopics") && root.get("importantTopics").isArray()) {
                for (JsonNode t : root.get("importantTopics")) {
                    importantTopics.add(t.asText());
                }
            }

            List<String> technicalTerms = new ArrayList<>();
            if (root.has("technicalTerms") && root.get("technicalTerms").isArray()) {
                for (JsonNode t : root.get("technicalTerms")) {
                    technicalTerms.add(t.asText());
                }
            }

            List<ConceptExplanation> conceptExplanations = new ArrayList<>();
            if (root.has("conceptExplanations") && root.get("conceptExplanations").isArray()) {
                for (JsonNode c : root.get("conceptExplanations")) {
                    conceptExplanations.add(ConceptExplanation.builder()
                            .concept(c.path("concept").asText())
                            .timestamp(c.path("timestamp").asText())
                            .simpleLanguageExplanation(c.path("simpleLanguageExplanation").asText())
                            .analogy(c.path("analogy").asText())
                            .build());
                }
            }

            List<VideoFlashcard> flashcards = new ArrayList<>();
            if (root.has("flashcards") && root.get("flashcards").isArray()) {
                for (JsonNode f : root.get("flashcards")) {
                    flashcards.add(VideoFlashcard.builder()
                            .front(f.path("front").asText())
                            .back(f.path("back").asText())
                            .topic(f.path("topic").asText())
                            .timestamp(f.path("timestamp").asText())
                            .build());
                }
            }

            return new VideoNotesResult(notes, importantTopics, technicalTerms, conceptExplanations, flashcards);

        } catch (Exception e) {
            log.error("Failed to generate notes and learning materials for '{}': {}", videoTitle, e.getMessage());
            return new VideoNotesResult(new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
    }

    private String extractJsonObject(String text) {
        if (text == null) return "{}";
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start != -1 && end != -1 && end > start) {
            return text.substring(start, end + 1);
        }
        return "{}";
    }

    public record VideoNotesResult(
            List<TopicNote> topicNotes,
            List<String> importantTopics,
            List<String> technicalTerms,
            List<ConceptExplanation> conceptExplanations,
            List<VideoFlashcard> flashcards
    ) {}
}
