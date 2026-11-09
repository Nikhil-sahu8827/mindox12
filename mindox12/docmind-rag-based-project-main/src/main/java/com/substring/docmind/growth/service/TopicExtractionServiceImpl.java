package com.substring.docmind.growth.service;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.growth.entity.StudyTopic;
import com.substring.docmind.growth.repository.StudyTopicRepository;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicExtractionServiceImpl implements TopicExtractionService {

    private final DocumentMetadataRepository documentMetadataRepository;
    private final StudyTopicRepository studyTopicRepository;

    @Autowired(required = false)
    private VectorStore vectorStore;

    @Autowired(required = false)
    private ChatModel chatModel;

    @PostConstruct
    public void init() {
        // Run scan on startup in a separate thread so startup is non-blocking
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait for database & vector store initialization
                scanAndExtractAllTopics();
            } catch (Exception e) {
                log.warn("Automatic topic detection initialization encountered an issue: {}", e.getMessage());
            }
        }).start();
    }

    @Override
    @Transactional
    public void scanAndExtractAllTopics() {
        log.info("Starting automatic file and topic detection across all platform study files...");
        List<DocumentMetadata> documents = documentMetadataRepository.findAll();
        int totalExtracted = 0;

        for (DocumentMetadata doc : documents) {
            try {
                List<StudyTopic> topics = extractTopicsForDocument(doc, doc.getUser());
                totalExtracted += topics.size();
            } catch (Exception e) {
                log.error("Failed to extract topics for document {}: {}", doc.getFilename(), e.getMessage());
            }
        }

        log.info("Automatic topic extraction complete. Processed {} documents, created/updated {} topics.",
                documents.size(), totalExtracted);
    }

    @Override
    @Transactional
    public List<StudyTopic> extractTopicsForDocument(DocumentMetadata document, User user) {
        if (document == null) return Collections.emptyList();

        String filename = document.getFilename();
        String docId = document.getId();
        boolean isGlobal = (user == null || user.getId() == 1 || (docId != null && docId.startsWith("sys_kb_")));

        // Check if topics already exist for this document to avoid re-extracting if unchanged
        List<StudyTopic> existing = studyTopicRepository.findByDocumentIdOrderByIdAsc(docId);
        if (!existing.isEmpty()) {
            return existing;
        }

        // Fetch sample text chunks for the document to analyze concepts
        List<Document> chunks = fetchDocumentChunks(docId);
        String fullSampleText = chunks.stream()
                .map(Document::getText)
                .filter(Objects::nonNull)
                .limit(8)
                .collect(Collectors.joining("\n\n"));

        List<ExtractedTopicStructure> structures = null;

        // Try AI-based structured extraction first if chat model is available
        if (chatModel != null && !fullSampleText.isBlank()) {
            try {
                structures = extractWithChatModel(filename, fullSampleText);
            } catch (Exception e) {
                log.warn("AI topic extraction failed for {}, using intelligent heuristic fallback: {}",
                        filename, e.getMessage());
            }
        }

        // Intelligent deterministic heuristic fallback if AI not available or failed
        if (structures == null || structures.isEmpty()) {
            structures = extractHeuristicTopics(filename, fullSampleText);
        }

        List<StudyTopic> savedTopics = new ArrayList<>();
        int orderIndex = 0;

        for (ExtractedTopicStructure struct : structures) {
            String normMainKey = normalizeKey(struct.getMainTopic());

            // Check if main topic already exists in platform to avoid duplicate creation
            StudyTopic mainTopic = studyTopicRepository.findByNormalizedKey(normMainKey)
                    .orElseGet(() -> StudyTopic.builder()
                            .document(document)
                            .user(user)
                            .sourceFileName(filename)
                            .topicName(struct.getMainTopic())
                            .hierarchyLevel(1)
                            .normalizedKey(normMainKey)
                            .description(struct.getDescription() != null ? struct.getDescription() : "Core concepts from " + filename)
                            .estimatedTotalQuestions(Math.max(15, struct.getSubtopics().size() * 5))
                            .isGlobal(isGlobal)
                            .orderIndex(0)
                            .build()
                    );

            if (mainTopic.getId() == null) {
                mainTopic.setOrderIndex(orderIndex++);
                mainTopic = studyTopicRepository.save(mainTopic);
            }
            savedTopics.add(mainTopic);

            // Save Subtopics (Hierarchy Level 2)
            int subOrder = 1;
            for (String subName : struct.getSubtopics()) {
                String normSubKey = normalizeKey(struct.getMainTopic() + " " + subName);
                final StudyTopic parentTopic = mainTopic;
                final int currentSubOrder = subOrder++;

                StudyTopic subTopic = studyTopicRepository.findByNormalizedKey(normSubKey)
                        .orElseGet(() -> StudyTopic.builder()
                                .document(document)
                                .user(user)
                                .sourceFileName(filename)
                                .topicName(parentTopic.getTopicName())
                                .subtopicName(subName)
                                .hierarchyLevel(2)
                                .parentTopicId(parentTopic.getId())
                                .normalizedKey(normSubKey)
                                .description("Subtopic focusing on " + subName)
                                .estimatedTotalQuestions(8)
                                .isGlobal(isGlobal)
                                .orderIndex(currentSubOrder)
                                .build()
                        );

                if (subTopic.getId() == null) {
                    subTopic = studyTopicRepository.save(subTopic);
                }
                savedTopics.add(subTopic);
            }
        }

        return savedTopics;
    }

    @Override
    @Transactional
    public void deleteTopicsForDocument(String documentId) {
        if (documentId != null) {
            log.info("Deleting study topics for document id: {}", documentId);
            studyTopicRepository.deleteByDocumentId(documentId);
        }
    }

    private List<Document> fetchDocumentChunks(String documentId) {
        if (vectorStore == null || documentId == null) return Collections.emptyList();
        try {
            return vectorStore.similaritySearch(
                    SearchRequest.builder()
                            .query("main concepts overview headings summary")
                            .topK(10)
                            .filterExpression("documentId == '" + documentId + "'")
                            .build()
            );
        } catch (Exception e) {
            log.debug("Similarity search chunk fetch for topic extraction returned empty: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    private List<ExtractedTopicStructure> extractWithChatModel(String filename, String sampleText) {
        ChatClient client = ChatClient.create(chatModel);
        String prompt = String.format("""
            You are a curriculum analysis engine. Analyze this study material:
            File: %s
            Text Excerpt:
            %s
            
            Extract 1 to 2 Main Topics and 3 to 5 Subtopics for each.
            Respond ONLY in valid JSON format:
            [
              {
                "mainTopic": "...",
                "description": "...",
                "subtopics": ["...", "...", "..."]
              }
            ]
            """, filename, sampleText.length() > 2500 ? sampleText.substring(0, 2500) : sampleText);

        String jsonResponse = client.prompt(prompt).call().content();
        return parseJsonTopics(jsonResponse);
    }

    private List<ExtractedTopicStructure> parseJsonTopics(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) return Collections.emptyList();
        List<ExtractedTopicStructure> list = new ArrayList<>();
        try {
            int start = rawJson.indexOf('[');
            int end = rawJson.lastIndexOf(']');
            if (start != -1 && end > start) {
                String clean = rawJson.substring(start, end + 1);
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                com.fasterxml.jackson.databind.JsonNode root = mapper.readTree(clean);
                if (root.isArray()) {
                    for (com.fasterxml.jackson.databind.JsonNode node : root) {
                        String main = node.path("mainTopic").asText();
                        String desc = node.path("description").asText();
                        List<String> subs = new ArrayList<>();
                        com.fasterxml.jackson.databind.JsonNode subNode = node.path("subtopics");
                        if (subNode.isArray()) {
                            for (com.fasterxml.jackson.databind.JsonNode s : subNode) {
                                subs.add(s.asText());
                            }
                        }
                        if (main != null && !main.isBlank()) {
                            list.add(new ExtractedTopicStructure(main, desc, subs));
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.debug("JSON parse of AI topics failed: {}", e.getMessage());
        }
        return list;
    }

    private List<ExtractedTopicStructure> extractHeuristicTopics(String filename, String text) {
        List<ExtractedTopicStructure> list = new ArrayList<>();
        String lowerFile = filename.toLowerCase();
        String lowerText = text.toLowerCase();

        if (lowerFile.contains("biology") || lowerText.contains("photosynthesis") || lowerText.contains("calvin")) {
            list.add(new ExtractedTopicStructure(
                    "Cellular Biology & Photosynthesis",
                    "Living systems, cellular energy generation, and Calvin cycle mechanisms",
                    Arrays.asList(
                            "Photosynthesis Light Reactions",
                            "Calvin Cycle & Carbon Fixation",
                            "Stomata & Transpiration Regulation",
                            "Cellular Respiration & ATP Synthesis"
                    )
            ));
        } else if (lowerFile.contains("cloud") || lowerText.contains("iaas") || lowerText.contains("paas") || lowerText.contains("elasticity")) {
            list.add(new ExtractedTopicStructure(
                    "Cloud Computing Architecture",
                    "Distributed computing, service models (IaaS/PaaS/SaaS), and auto-scaling elasticity",
                    Arrays.asList(
                            "Cloud Service Models (IaaS, PaaS, SaaS)",
                            "Deployment Models (Public, Private, Hybrid)",
                            "Elasticity vs Scalability",
                            "Cloud Security & Storage Architecture"
                    )
            ));
        } else if (lowerFile.contains("exception") || lowerFile.contains("bca") || lowerText.contains("try-catch") || lowerText.contains("throws")) {
            list.add(new ExtractedTopicStructure(
                    "Java Exception Handling",
                    "Robust software engineering with try, catch, finally, and custom exception hierarchies",
                    Arrays.asList(
                            "try-catch-finally Block Mechanics",
                            "throw vs throws Keywords",
                            "Checked vs Unchecked Exceptions",
                            "Custom Application Exceptions"
                    )
            ));
        } else if (lowerFile.contains("memory") || lowerFile.contains("architecture") || lowerText.contains("database")) {
            list.add(new ExtractedTopicStructure(
                    "Database & AI Memory Architecture",
                    "State persistence, context window retention, and vector store integration",
                    Arrays.asList(
                            "Relational Schema Design & JPA",
                            "Context Memory Window Optimization",
                            "Vector Store Embeddings & Indexing"
                    )
            ));
        } else if (lowerFile.contains("isolation") || lowerText.contains("multi-tenant") || lowerText.contains("security")) {
            list.add(new ExtractedTopicStructure(
                    "Multi-Tenant Security & Access Control",
                    "Data isolation, role-based authorization, and tenant partition guarantees",
                    Arrays.asList(
                            "JWT Authentication & Session Context",
                            "Document Ownership & Tenant Isolation",
                            "HNSW Vector Space Security Filtering"
                    )
            ));
        } else if (lowerFile.contains("employee") || lowerFile.contains("csv")) {
            list.add(new ExtractedTopicStructure(
                    "Organizational Data Analysis",
                    "Structured organizational records, department compensation, and employee roles",
                    Arrays.asList(
                            "Department Structures & Headcounts",
                            "Compensation & Role Categorization",
                            "Record Filtering & Data Aggregation"
                    )
            ));
        } else {
            // General clean topic name from filename
            String cleanTitle = filename.replaceAll("(?i)\\[youtube\\]\\s*", "")
                    .replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "")
                    .replaceAll("[_-]+", " ").trim();
            cleanTitle = capitalizeWords(cleanTitle);

            // Attempt to extract subheadings from markdown or lines
            List<String> subtopics = new ArrayList<>();
            Pattern p = Pattern.compile("(?m)^(?:#{1,3}|\\d+\\.|[A-Z][a-zA-Z\\s]{3,30}:)\\s*(.+)$");
            Matcher m = p.matcher(text);
            while (m.find() && subtopics.size() < 4) {
                String candidate = m.group(1).trim();
                if (candidate.length() > 3 && candidate.length() < 50 && !subtopics.contains(candidate)) {
                    subtopics.add(candidate);
                }
            }

            if (subtopics.isEmpty()) {
                subtopics = Arrays.asList(
                        "Core Principles & Definitions",
                        "Key Mechanisms & Workflows",
                        "Implementation & Best Practices"
                );
            }

            list.add(new ExtractedTopicStructure(
                    cleanTitle,
                    "Study concepts and practice principles derived from " + filename,
                    subtopics
            ));
        }

        return list;
    }

    private String normalizeKey(String text) {
        if (text == null) return "unknown";
        return text.trim().toLowerCase().replaceAll("[^a-z0-9]", "");
    }

    private String capitalizeWords(String text) {
        if (text == null || text.isBlank()) return "General Topic";
        String[] words = text.split("\\s+");
        StringBuilder sb = new StringBuilder();
        for (String w : words) {
            if (!w.isBlank()) {
                sb.append(Character.toUpperCase(w.charAt(0)))
                        .append(w.substring(1).toLowerCase())
                        .append(" ");
            }
        }
        return sb.toString().trim();
    }

    @lombok.AllArgsConstructor
    @lombok.Getter
    private static class ExtractedTopicStructure {
        private String mainTopic;
        private String description;
        private List<String> subtopics;
    }
}
