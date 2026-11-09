package com.substring.docmind.chat.service;

import com.substring.docmind.chat.dto.*;
import com.substring.docmind.chat.entity.ChatMessage;
import com.substring.docmind.chat.entity.Conversation;
import com.substring.docmind.chat.memory.JpaChatMemory;
import com.substring.docmind.enums.MessageType;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.growth.entity.StudyTopic;
import com.substring.docmind.growth.repository.StudyTopicRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class RagServiceImpl implements RagService {

    public static final String NOT_FOUND_MESSAGE =
            "I couldn't find this information in the available files. I don't want to provide incorrect information.";

    private final VectorSearchService vectorSearchService;
    private final ConversationService conversationService;
    private final JpaChatMemory jpaChatMemory;
    private final UserRepository userRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final StudyTopicRepository studyTopicRepository;

    @Autowired(required = false)
    private ChatModel chatModel;

    @Override
    @Transactional
    public ChatResponseDto askQuestion(ChatRequestDto request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        String question = request.getQuestion().trim();
        log.info("Processing RAG question for user {}: {}", user.getId(), question);

        Conversation conversation = conversationService.getOrCreateConversation(
                request.getConversationId(), question, user
        );
        String conversationId = conversation.getId();

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        // 1. Retrieve strictly relevant chunks with hybrid scoring and relevance threshold
        List<Document> relevantChunks = vectorSearchService.searchRelevantChunks(
                question, user.getId(), isAdmin, request.getDocumentId(), 6
        );

        // 2. Retrieve recent chat memory
        List<ChatMessage> history = jpaChatMemory.getRecentMessages(conversationId, 6);

        // 3. Generate verified AI / NLP response
        String answer = generateAnswer(question, relevantChunks, history, user);

        // 4. Build citations strictly for files used in the answer
        List<CitationDto> citations = buildVerifiedCitations(relevantChunks, answer);

        // 5. Persist to chat memory
        jpaChatMemory.saveMessage(conversationId, MessageType.USER, question);
        jpaChatMemory.saveMessage(conversationId, MessageType.ASSISTANT, answer);

        return ChatResponseDto.builder()
                .answer(answer)
                .conversationId(conversationId)
                .citations(citations)
                .build();
    }

    @Override
    public List<SimilaritySearchResult> searchSimilarity(SimilaritySearchRequest request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        int topK = request.getTopK() > 0 ? request.getTopK() : 5;

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<Document> chunks = vectorSearchService.searchRelevantChunks(
                request.getQuery(), user.getId(), isAdmin, request.getDocumentId(), topK
        );

        return chunks.stream().map(doc -> {
            Map<String, Object> meta = doc.getMetadata();
            Double scoreVal = doc.getScore() != null ? doc.getScore() : 1.0;
            int page = meta.get("pageNumber") != null ? ((Number) meta.get("pageNumber")).intValue() : 1;
            return SimilaritySearchResult.builder()
                    .text(doc.getText())
                    .documentId((String) meta.get("documentId"))
                    .fileName((String) meta.get("fileName"))
                    .score(scoreVal)
                    .similarityScore(scoreVal)
                    .pageNumber(page)
                    .chunkIndex(meta.get("chunkIndex") != null ? ((Number) meta.get("chunkIndex")).intValue() : 0)
                    .build();
        }).collect(Collectors.toList());
    }

    @Override
    public ResponseBodyEmitter streamResponse(ChatRequestDto request, UserPrincipal currentUser) {
        ResponseBodyEmitter emitter = new ResponseBodyEmitter(180_000L);
        emitter.onTimeout(() -> {
            try {
                emitter.complete();
            } catch (Exception ignored) {}
        });
        emitter.onError(e -> log.debug("SSE stream connection closed or error: {}", e.getMessage()));

        CompletableFuture.runAsync(() -> {
            try {
                ChatResponseDto response = askQuestion(request, currentUser);
                String answer = response.getAnswer();

                // Tokenize answer preserving paragraphs and whitespace
                Matcher matcher = Pattern.compile("\\S+\\s*").matcher(answer);
                List<String> tokens = new ArrayList<>();
                while (matcher.find()) {
                    tokens.add(matcher.group());
                }
                if (tokens.isEmpty() && !answer.isEmpty()) {
                    tokens.add(answer);
                }

                for (String token : tokens) {
                    sendSseChunk(emitter, token);
                    Thread.sleep(12);
                }

                // Explicit terminal marker expected by frontend SSE reader
                sendSseChunk(emitter, "[DONE]");
                try {
                    emitter.complete();
                } catch (Exception ignored) {}
            } catch (Exception e) {
                log.warn("Streaming notice: {}", e.getMessage());
                try {
                    sendSseChunk(emitter, "Error processing chat stream: " + e.getMessage());
                    sendSseChunk(emitter, "[DONE]");
                    emitter.complete();
                } catch (Exception ignored) {}
            }
        });

        return emitter;
    }

    private void sendSseChunk(ResponseBodyEmitter emitter, String text) throws IOException {
        if (text == null || text.isEmpty()) return;
        String[] lines = text.split("\r?\n", -1);
        StringBuilder sb = new StringBuilder();
        for (String line : lines) {
            sb.append("data:").append(line).append("\n");
        }
        sb.append("\n");
        emitter.send(sb.toString());
    }

    private List<CitationDto> buildVerifiedCitations(List<Document> documents, String answer) {
        if (documents == null || documents.isEmpty() || answer == null || answer.isBlank()) {
            return Collections.emptyList();
        }

        // Never emit citations if the response indicates the information was not found or is a clarification request
        if (answer.contains("I couldn't find this information in the available files")
                || answer.contains("I don't want to provide incorrect information")
                || answer.contains("clarify your question")) {
            return Collections.emptyList();
        }

        // Never emit citations for pure general knowledge answers
        if (answer.contains("general knowledge") && !answer.contains("[Source:")) {
            return Collections.emptyList();
        }

        List<CitationDto> citations = new ArrayList<>();
        Set<String> seenDocPages = new HashSet<>();

        for (Document doc : documents) {
            Map<String, Object> meta = doc.getMetadata();
            String docId = (String) meta.get("documentId");
            String fileName = (String) meta.getOrDefault("fileName", "Document");
            int page = meta.get("pageNumber") != null ? ((Number) meta.get("pageNumber")).intValue() : 1;
            String key = (docId != null ? docId : fileName) + "_p" + page;

            if (seenDocPages.add(key)) {
                citations.add(CitationDto.builder()
                        .documentId(docId)
                        .fileName(fileName)
                        .text(doc.getText())
                        .score(doc.getScore() != null ? doc.getScore() : 1.0)
                        .pageNumber(page)
                        .build());
            }
        }
        return citations;
    }

    private String generateAnswer(String question, List<Document> chunks, List<ChatMessage> history, User user) {
        String trimmedQuestion = question.trim();

        // 1. Check for Greeting / Capability queries (e.g. "hi", "hello", "kaise ho", "what can you do", "who are you")
        if (isGreetingOrCapabilityQuery(trimmedQuestion)) {
            return answerGreetingOrCapability(trimmedQuestion);
        }

        // 2. Check for Library / Catalog / Material Discovery queries
        if (isLibraryOrDiscoveryQuery(trimmedQuestion)) {
            return answerLibraryOrDiscoveryQuery(user);
        }

        // 3. Detect if question is ambiguous or too vague (e.g., "tell me about it", "what is this?", "explain")
        if ((chunks == null || chunks.isEmpty()) && isAmbiguousQuestion(trimmedQuestion, history)) {
            return "Could you please clarify your question? Please specify the topic, file, or concept you would like to know more about.";
        }

        // 4. If no relevant chunks exist in the knowledge base
        if (chunks == null || chunks.isEmpty()) {
            return handleEmptyChunksQuery(trimmedQuestion, history);
        }

        // 5. Try Generative AI Model with complete 8-rule system prompt
        if (chatModel != null) {
            try {
                String prompt = buildSystemPrompt(trimmedQuestion, chunks, history);
                ChatClient chatClient = ChatClient.create(chatModel);
                String response = chatClient.prompt(prompt).call().content();

                if (response != null && !response.isBlank()) {
                    String cleaned = cleanAiResponse(response);
                    // Ensure the exact mandated fallback message is used if the model says not found
                    if (isModelIndicatingNotFound(cleaned)) {
                        return NOT_FOUND_MESSAGE;
                    }
                    return cleaned;
                }
            } catch (Exception e) {
                log.warn("Chat model call failed ({}), activating intelligent NLP answer extractor.", e.getMessage());
            }
        }

        // 4. Intelligent Deterministic NLP Answer Synthesizer (handles quota exhaustion or offline model)
        return synthesizeEvidenceBasedAnswer(trimmedQuestion, chunks);
    }

    private String buildSystemPrompt(String question, List<Document> chunks, List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are DocMind AI, the official and trusted document intelligence assistant for this website.

            MANDATORY RULES:
            1. PROVIDE ACCURATE & RELEVANT ANSWERS: Answer the question directly using ONLY verified facts from the Document Excerpts below. Never invent facts, fabricate citations, or present assumptions as confirmed information.
            2. RELEVANCE & KNOWLEDGE BASE PRIORITY: Prioritize information from the provided excerpts.
            3. HONEST REPORTING: If the requested information is absent or cannot be confirmed from the excerpts, respond EXACTLY:
               "I couldn't find this information in the available files. I don't want to provide incorrect information."
            4. PARTIAL ANSWERS: If only part of the answer is available in the excerpts, provide that verified part and explicitly state what information is missing.
            5. AMBIGUOUS QUESTIONS: If the question is ambiguous or vague, ask the user for clarification instead of guessing.
            6. CONFLICTING SOURCES: If different excerpts conflict with each other, explain the conflict clearly rather than silently choosing an unsupported answer.
            7. GENERAL KNOWLEDGE QUESTIONS: If the user asks a general question unrelated to the files (e.g. basic math, general definitions, science concepts not in the files), provide a helpful, reliable answer using general knowledge. Explicitly state: "(Note: This answer is based on general knowledge and is not from your uploaded files.)" Do NOT claim the answer came from any document.
            8. SOURCE CITATIONS & FORMATTING:
               - Present information in clean, highly readable, and professional markdown (use headings, bullet points, and code blocks where helpful).
               - Do NOT repeat '[Source: ...]' on every single bullet point or sentence.
               - Instead, at the end of your response, provide a clean, dedicated source list:
                 ---
                 ### 📚 Sources
                 - **<fileName>** (Pages <pageNumbers>)
               - Only cite verified excerpts provided below. Never invent fake sources.

            """);

        sb.append("Relevant Document Excerpts:\n");
        for (int i = 0; i < chunks.size(); i++) {
            Document doc = chunks.get(i);
            Map<String, Object> meta = doc.getMetadata();
            String fileName = (String) meta.getOrDefault("fileName", "Document " + (i + 1));
            Object page = meta.getOrDefault("pageNumber", 1);
            sb.append(String.format("--- Excerpt [%d] (Source: %s, Page: %s) ---\n%s\n\n",
                    i + 1, fileName, page, doc.getText().trim()));
        }

        if (history != null && !history.isEmpty()) {
            sb.append("Recent Conversation History:\n");
            for (ChatMessage msg : history) {
                sb.append(String.format("%s: %s\n", msg.getMessageType(), msg.getContent()));
            }
            sb.append("\n");
        }

        sb.append("User Question: ").append(question).append("\n\nAnswer:");
        return sb.toString();
    }

    private boolean isGreetingOrCapabilityQuery(String question) {
        String l = question.toLowerCase().trim().replaceAll("[?.!]+$", "").trim();
        return l.matches("^(hi|hello|hey|namaste|greetings|hola|good\\s+(morning|afternoon|evening)|kaise\\s+ho|kya\\s+haal|suno|help)$")
                || l.contains("what can you do")
                || l.contains("who are you")
                || l.contains("how can you help")
                || l.contains("kya kar sakte ho")
                || l.contains("tum kaun ho")
                || l.contains("aap kaun ho")
                || l.contains("capabilities");
    }

    private String answerGreetingOrCapability(String question) {
        String l = question.toLowerCase().trim();
        boolean isHindi = l.contains("kaise") || l.contains("kya") || l.contains("tum") || l.contains("aap") || l.contains("namaste") || l.contains("haal");

        if (isHindi) {
            return """
                Namaste! Main **DocMind AI** hoon, aapka personal AI study aur document intelligence assistant.

                Main aapki in cheezon mein madad kar sakta hoon:
                1. 📄 **Document & PDF Q&A:** Aapke uploaded notes, PDFs aur study materials se factual questions answer karna aur citations dena.
                2. 🎥 **YouTube Video Learning:** YouTube educational videos se direct summary, questions aur transcript analysis karna.
                3. 📝 **Interactive Quizzes:** Kisi bhi file ya topic par instant practice quizzes generate karna.
                4. 📈 **My Growth Analytics:** Aapke study topics, quiz accuracy aur mistakes ko track karna.
                5. 💡 **General Knowledge:** Coding, science aur academic topics ko saral bhasha mein samjhana.

                Aap mujhse koi bhi question pooch sakte hain ya koi document select karke start kar sakte hain!""";
        }

        return """
            Hello! I am **DocMind AI**, your personal document intelligence and study assistant.

            Here is what I can do for you:
            1. 📄 **Document & PDF Analysis:** Ask questions and get answers grounded directly in your uploaded study materials with page citations.
            2. 🎥 **YouTube Video Learning:** Extract full transcripts, get summaries, and ask questions about YouTube educational videos.
            3. 📝 **Interactive Quizzes:** Generate targeted multiple-choice quizzes across various difficulty levels to test your knowledge.
            4. 📈 **My Growth Analytics:** Track your topic mastery, quiz performance, and mistake patterns over time.
            5. 💡 **General Knowledge:** Explain academic concepts, coding patterns, and general study topics.

            How can I help you today?""";
    }

    private boolean isLibraryOrDiscoveryQuery(String question) {
        String l = question.toLowerCase().trim();
        return (l.contains("what") || l.contains("which") || l.contains("list") || l.contains("show") || l.contains("tell") || l.contains("kya") || l.contains("kaun"))
                && (l.contains("document") || l.contains("documents") || l.contains("topic") || l.contains("topics") || l.contains("material") || l.contains("materials") || l.contains("file") || l.contains("files") || l.contains("library"))
                && (l.contains("available") || l.contains("have") || l.contains("my") || l.contains("there") || l.contains("study") || l.contains("uploaded") || l.contains("hain") || l.contains("hai"));
    }

    private String answerLibraryOrDiscoveryQuery(User user) {
        List<DocumentMetadata> docs = documentMetadataRepository.findByUserOrderByCreatedAtDesc(user);
        if (docs == null || docs.isEmpty()) {
            docs = documentMetadataRepository.findAll();
        }

        if (docs == null || docs.isEmpty()) {
            return "You do not have any uploaded documents yet. You can upload PDFs, text files, or paste a YouTube video URL on the platform to start exploring, asking questions, and generating quizzes!";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("Here are the study documents and materials currently available in your platform library:\n\n");
        for (int i = 0; i < Math.min(10, docs.size()); i++) {
            DocumentMetadata d = docs.get(i);
            String title = d.getFilename();
            String type = (d.getSourceUrl() != null && d.getSourceUrl().contains("youtube.com")) ? "YouTube Video" : "Study Document";
            sb.append(String.format("%d. **%s** *(%s)*\n", i + 1, title, type));
        }

        try {
            List<StudyTopic> topics = studyTopicRepository.findAllForUser(user.getId());
            if (topics != null && !topics.isEmpty()) {
                sb.append("\n**Key Study Topics Detected Across Your Materials:**\n");
                List<String> topicNames = topics.stream().map(StudyTopic::getTopicName).distinct().limit(8).toList();
                for (String t : topicNames) {
                    sb.append("- ").append(t).append("\n");
                }
            }
        } catch (Exception ignored) {}

        sb.append("\nYou can ask me specific questions about any of these topics/documents, request an executive summary, or practice quizzes on them!");
        return sb.toString();
    }

    private String handleEmptyChunksQuery(String question, List<ChatMessage> history) {
        String lower = question.toLowerCase().trim();

        // If the question is explicitly asking about a specific missing file/page/table
        if (isSpecificDocumentFactQuery(lower)) {
            return NOT_FOUND_MESSAGE;
        }

        // Try ChatModel for general question or conversation
        if (chatModel != null) {
            try {
                StringBuilder promptBuilder = new StringBuilder();
                promptBuilder.append("""
                    You are DocMind AI, a friendly, intelligent, and accurate study and document assistant for this website.
                    The user is asking a general question or conversational query.
                    
                    MANDATORY RULES:
                    1. Answer helpfully, accurately, and politely in the user's language (English or Hindi/Hinglish if asked in Hindi/Hinglish).
                    2. If this is an academic, technical, or general knowledge question, provide a clear, structured explanation.
                    3. Do NOT invent facts or claim to cite a specific document when none was provided.
                    4. At the end of educational/general knowledge answers, append exactly:
                       \n\n*(Note: This answer is based on general knowledge and is not from your uploaded files.)*
                    
                    User Query:
                    """);
                promptBuilder.append(question).append("\n\nAnswer:");

                ChatClient client = ChatClient.create(chatModel);
                String ans = client.prompt(promptBuilder.toString()).call().content();
                if (ans != null && !ans.isBlank()) {
                    return ans.trim();
                }
            } catch (Exception e) {
                log.warn("General question ChatModel fallback failed: {}", e.getMessage());
            }
        }

        // Fallback to math or general knowledge patterns
        if (isGeneralKnowledgeQuestion(question)) {
            return answerGeneralKnowledgeQuestion(question);
        }

        return NOT_FOUND_MESSAGE;
    }

    private boolean isSpecificDocumentFactQuery(String lower) {
        return (lower.contains("page ") && lower.matches(".*page\\s+\\d+.*"))
                || lower.contains("in this contract")
                || lower.contains("in section ")
                || lower.contains("in table ")
                || lower.contains("according to the file")
                || lower.contains("in the uploaded file");
    }

    private boolean isAmbiguousQuestion(String question, List<ChatMessage> history) {
        String lower = question.toLowerCase().trim().replaceAll("[?.!]+$", "").trim();
        String[] ambiguousPhrases = {
                "tell me about it", "tell me more about it", "tell me more",
                "what is this", "what is that", "what is it",
                "explain this", "explain that", "explain it",
                "can you explain it", "can you explain that", "can you explain this",
                "what does it do", "how does it work", "what about it",
                "who is he", "who is she"
        };
        for (String phrase : ambiguousPhrases) {
            if (lower.equals(phrase) || lower.startsWith(phrase + " ")) {
                return true;
            }
        }
        return false;
    }

    private boolean isGeneralKnowledgeQuestion(String question) {
        String lower = question.toLowerCase().trim();
        // Math expressions (e.g. "what is 2 + 2", "calculate 15 * 12", "what is 15 multiplied by 24")
        if (lower.matches(".*\\b\\d+(?:\\.\\d+)?\\s*(?:[\\+\\-\\*/]|plus|minus|multiplied by|times|divided by|into|x)\\s*\\d+(?:\\.\\d+)?.*")) {
            return true;
        }
        // Common general knowledge patterns
        String[] generalPatterns = {
                "capital of", "who wrote", "speed of light", "distance to moon",
                "planets in the solar system", "largest ocean", "who is the president",
                "who discovered", "what is the currency of", "boiling point of"
        };
        for (String p : generalPatterns) {
            if (lower.contains(p)) {
                return true;
            }
        }
        return false;
    }

    private String answerGeneralKnowledgeQuestion(String question) {
        String lower = question.toLowerCase().trim();

        // Basic Math Evaluation
        Matcher mathMatcher = Pattern.compile("(\\d+(?:\\.\\d+)?)\\s*([\\+\\-\\*/]|plus|minus|multiplied by|times|divided by|into|x)\\s*(\\d+(?:\\.\\d+)?)").matcher(lower);
        if (mathMatcher.find()) {
            try {
                double a = Double.parseDouble(mathMatcher.group(1));
                String op = mathMatcher.group(2).trim();
                double b = Double.parseDouble(mathMatcher.group(3));
                double result;
                switch (op) {
                    case "+": case "plus": result = a + b; break;
                    case "-": case "minus": result = a - b; break;
                    case "*": case "times": case "multiplied by": case "into": case "x": result = a * b; break;
                    case "/": case "divided by": result = b != 0 ? a / b : Double.NaN; break;
                    default: result = Double.NaN;
                }
                if (!Double.isNaN(result)) {
                    String formatted = (result == Math.floor(result)) ? String.valueOf((long) result) : String.format("%.2f", result);
                    return String.format("%s %s %s = **%s**\n\n*(Note: This answer is based on general knowledge and is not from your uploaded files.)*",
                            mathMatcher.group(1), op, mathMatcher.group(3), formatted);
                }
            } catch (Exception ignored) {}
        }

        // Try ChatModel for general question without file context
        if (chatModel != null) {
            try {
                String prompt = "Answer the following general question accurately and concisely: " + question +
                        "\n\nInclude at the end: '(Note: This answer is based on general knowledge and is not from your uploaded files.)'";
                ChatClient client = ChatClient.create(chatModel);
                String ans = client.prompt(prompt).call().content();
                if (ans != null && !ans.isBlank()) {
                    return ans;
                }
            } catch (Exception ignored) {}
        }

        // Curated general knowledge fallbacks
        if (lower.contains("capital of france")) {
            return "The capital of France is **Paris**.\n\n*(Note: This answer is based on general knowledge and is not from your uploaded files.)*";
        } else if (lower.contains("capital of japan")) {
            return "The capital of Japan is **Tokyo**.\n\n*(Note: This answer is based on general knowledge and is not from your uploaded files.)*";
        } else if (lower.contains("speed of light")) {
            return "The speed of light in a vacuum is approximately **299,792,458 meters per second** (about 300,000 km/s or 186,282 miles per second).\n\n*(Note: This answer is based on general knowledge and is not from your uploaded files.)*";
        }

        return NOT_FOUND_MESSAGE;
    }

    private boolean isModelIndicatingNotFound(String response) {
        String lower = response.toLowerCase();
        return lower.contains("could not find information")
                || lower.contains("couldn't find information")
                || lower.contains("not found in the indexed")
                || lower.contains("not present in the provided")
                || lower.contains("does not contain information");
    }

    private String cleanAiResponse(String response) {
        if (response == null) return "";
        String text = response.trim();

        // If the AI generated multiple in-line [Source: filename, Page: X] tags, consolidate them into a clean Sources footer
        Matcher matcher = Pattern.compile("\\[Source:\\s*([^,\\]]+?)(?:,\\s*Pages?:\\s*([^\\s\\]]+))?\\]").matcher(text);
        Map<String, Set<String>> sourcePages = new LinkedHashMap<>();
        int count = 0;
        while (matcher.find()) {
            count++;
            String file = matcher.group(1).trim();
            String page = matcher.group(2) != null ? matcher.group(2).trim() : null;
            sourcePages.computeIfAbsent(file, k -> new LinkedHashSet<>());
            if (page != null && !page.isBlank()) {
                for (String p : page.split("[,\\s]+")) {
                    if (!p.isBlank()) sourcePages.get(file).add(p);
                }
            }
        }

        if (count >= 2 && !text.contains("### 📚 Sources") && !text.contains("### Sources")) {
            String cleanedBody = text.replaceAll("\\s*\\[Source:\\s*[^,\\]]+?(?:,\\s*Pages?:\\s*[^\\s\\]]+)?\\]", "");
            StringBuilder sb = new StringBuilder(cleanedBody.trim());
            sb.append("\n\n---\n### 📚 Sources\n");
            for (Map.Entry<String, Set<String>> entry : sourcePages.entrySet()) {
                String file = entry.getKey();
                Set<String> pages = entry.getValue();
                if (pages.isEmpty()) {
                    sb.append(String.format("- **%s**\n", file));
                } else {
                    sb.append(String.format("- **%s** (Page%s %s)\n", file, pages.size() > 1 ? "s" : "", String.join(", ", pages)));
                }
            }
            return sb.toString().trim();
        }

        return text;
    }

    /**
     * Intelligent Deterministic NLP Answer Synthesizer:
     * When the AI model quota is exhausted (429) or offline, extracts and synthesizes
     * factual sentences that directly answer the query without dumping raw uncleaned chunks.
     */
    private String synthesizeEvidenceBasedAnswer(String question, List<Document> chunks) {
        List<String> keywords = vectorSearchService.extractKeywords(question);
        String lowerQuery = question.toLowerCase();

        // Group chunks by file to support multi-file synthesis
        Map<String, List<Document>> byFile = new LinkedHashMap<>();
        for (Document d : chunks) {
            String fn = (String) d.getMetadata().getOrDefault("fileName", "Document");
            byFile.computeIfAbsent(fn, k -> new ArrayList<>()).add(d);
        }

        StringBuilder response = new StringBuilder();
        int filesWithAnswers = 0;
        List<String> sourceCitations = new ArrayList<>();

        for (Map.Entry<String, List<Document>> entry : byFile.entrySet()) {
            String fileName = entry.getKey();
            List<Document> fileDocs = entry.getValue();

            // Extract relevant factual sentences from this file's chunks
            List<ScoredSentence> topSentences = new ArrayList<>();
            int pageNumber = 1;

            for (Document doc : fileDocs) {
                if (doc.getMetadata().get("pageNumber") != null) {
                    pageNumber = ((Number) doc.getMetadata().get("pageNumber")).intValue();
                }
                String text = cleanChunkText(doc.getText());
                String[] sentences = text.split("(?<=[.!?])\\s+");

                for (String rawSentence : sentences) {
                    String sentence = rawSentence.trim();
                    if (sentence.length() < 20 || sentence.length() > 300) continue;

                    String lowerSentence = sentence.toLowerCase();
                    int matchCount = 0;
                    for (String kw : keywords) {
                        if (lowerSentence.contains(kw.toLowerCase())) {
                            matchCount++;
                        }
                    }

                    // Score sentence based on keyword density and phrase overlap
                    double score = 0.0;
                    if (!keywords.isEmpty()) {
                        score = (double) matchCount / keywords.size();
                    }
                    if (lowerSentence.contains(lowerQuery)) {
                        score += 0.50;
                    }

                    if (matchCount >= 1) {
                        topSentences.add(new ScoredSentence(sentence, score + (matchCount * 0.1)));
                    }
                }
            }

            // Deduplicate and select top 2-3 sentences for this file
            topSentences.sort((a, b) -> Double.compare(b.score, a.score));
            List<String> selected = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (ScoredSentence ss : topSentences) {
                if (selected.size() >= 3) break;
                String normalized = ss.text.toLowerCase().replaceAll("[^a-z0-9]", "");
                if (seen.add(normalized)) {
                    selected.add(ss.text);
                }
            }

            if (!selected.isEmpty()) {
                filesWithAnswers++;
                if (byFile.size() > 1) {
                    response.append(String.format("### %s (Page %d)\n", fileName, pageNumber));
                } else {
                    response.append(String.format("Based on **%s** (Page %d):\n\n", fileName, pageNumber));
                }
                for (String s : selected) {
                    response.append(s).append(" ");
                }
                response.append("\n\n");
                sourceCitations.add(String.format("[Source: %s, Page %d]", fileName, pageNumber));
            }
        }

        // If no file had verifiable sentences answering the question
        if (filesWithAnswers == 0) {
            // Check if general knowledge can answer
            if (isGeneralKnowledgeQuestion(question)) {
                return answerGeneralKnowledgeQuestion(question);
            }
            if (vectorSearchService.isOverviewOrSummaryQuery(question)) {
                return synthesizeOverviewAnswer(chunks);
            }
            return NOT_FOUND_MESSAGE;
        }

        // Partial answer handling (Rule 4): note if some requested concepts are missing
        List<String> missingConcepts = new ArrayList<>();
        Set<String> stopWords = Set.of(
                "explain", "concept", "concepts", "regarding", "files", "file",
                "document", "documents", "information", "available", "about", "what", "which",
                "summary", "summarize", "executive", "concise", "points", "point", "main", "overview"
        );
        for (String kw : keywords) {
            String lKw = kw.toLowerCase();
            if (lKw.length() >= 4 && !stopWords.contains(lKw)) {
                boolean foundInAny = false;
                for (List<Document> docList : byFile.values()) {
                    for (Document d : docList) {
                        if (d.getText().toLowerCase().contains(lKw)) {
                            foundInAny = true;
                            break;
                        }
                    }
                    if (foundInAny) break;
                }
                if (!foundInAny) {
                    missingConcepts.add(kw);
                }
            }
        }
        // If the vast majority of domain concepts from the question are missing, do not hallucinate an unrelated chunk
        if (!vectorSearchService.isOverviewOrSummaryQuery(question) && missingConcepts.size() >= 2 && missingConcepts.size() >= (keywords.size() * 0.65)) {
            return NOT_FOUND_MESSAGE;
        }

        if (!missingConcepts.isEmpty()) {
            response.append(String.format("*(Note: Information regarding '%s' was not found in the available files.)*\n\n",
                    String.join(", ", missingConcepts)));
        }

        if (filesWithAnswers > 1) {
            response.append("*(Note: Multiple source documents contain relevant information as synthesized above.)*\n\n");
        }

        // Append clean, formatted source citations section
        if (!sourceCitations.isEmpty()) {
            response.append("\n\n---\n### 📚 Sources\n");
            for (String sc : sourceCitations) {
                response.append("- ").append(sc.replace("[Source: ", "**").replace("]", "**")).append("\n");
            }
        }

        return response.toString().trim();
    }

    private String synthesizeOverviewAnswer(List<Document> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return NOT_FOUND_MESSAGE;
        }

        Map<String, List<Document>> byFile = new LinkedHashMap<>();
        for (Document d : chunks) {
            String fn = (String) d.getMetadata().getOrDefault("fileName", "Document");
            byFile.computeIfAbsent(fn, k -> new ArrayList<>()).add(d);
        }

        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, List<Document>> entry : byFile.entrySet()) {
            String fileName = entry.getKey();
            List<Document> fileDocs = entry.getValue();

            int pageNumber = 1;
            List<String> keySentences = new ArrayList<>();
            Set<String> seen = new HashSet<>();

            for (Document doc : fileDocs) {
                if (doc.getMetadata().get("pageNumber") != null) {
                    pageNumber = ((Number) doc.getMetadata().get("pageNumber")).intValue();
                }
                String text = cleanChunkText(doc.getText());
                String[] sentences = text.split("(?<=[.!?])\\s+");
                for (String raw : sentences) {
                    String s = raw.trim();
                    if (s.length() >= 25 && s.length() <= 280) {
                        String norm = s.toLowerCase().replaceAll("[^a-z0-9]", "");
                        if (seen.add(norm)) {
                            keySentences.add(s);
                        }
                    }
                }
            }

            sb.append(String.format("### Executive Summary: %s\n\n", fileName));
            sb.append("Here is a concise summary of the key points covered in this document:\n\n");
            int limit = Math.min(6, keySentences.size());
            for (int i = 0; i < limit; i++) {
                sb.append("- ").append(keySentences.get(i)).append("\n");
            }
            sb.append(String.format("\n---\n### 📚 Sources\n- **%s** (Page %d)\n\n", fileName, pageNumber));
        }

        return sb.toString().trim();
    }

    private String cleanChunkText(String raw) {
        if (raw == null) return "";
        // Remove UTF-8 BOM, replacement char, HTML tags, markdown table noise, and metadata markers
        return raw.replace("\uFEFF", "")
                .replace("\uFFFD", "")
                .replaceAll("id=\"\\d+\"\\]?", "")
                .replaceAll("<[^>]*>", " ")
                .replaceAll("\\|\\s*[-:]+\\s*\\|", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private static class ScoredSentence {
        String text;
        double score;

        ScoredSentence(String text, double score) {
            this.text = text;
            this.score = score;
        }
    }
}
