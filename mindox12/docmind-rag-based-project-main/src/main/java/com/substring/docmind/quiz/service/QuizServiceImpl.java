package com.substring.docmind.quiz.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.enums.DifficultyLevel;
import com.substring.docmind.exception.BadRequestException;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.quiz.dto.*;
import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizMistake;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.quiz.entity.SavedQuestion;
import com.substring.docmind.quiz.repository.QuizMistakeRepository;
import com.substring.docmind.quiz.repository.QuizQuestionRepository;
import com.substring.docmind.quiz.repository.QuizRepository;
import com.substring.docmind.quiz.repository.SavedQuestionRepository;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QuizServiceImpl implements QuizService {

    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final SavedQuestionRepository savedQuestionRepository;
    private final QuizMistakeRepository quizMistakeRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final UserRepository userRepository;
    private final VectorStore vectorStore;
    private final JdbcTemplate jdbcTemplate;
    private final QuizPdfService quizPdfService;
    private final com.substring.docmind.quiz.repository.QuizPdfRepository quizPdfRepository;
    private final com.substring.docmind.document.service.YouTubeService youTubeService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Autowired(required = false)
    private ChatModel chatModel;

    @Autowired(required = false)
    private com.substring.docmind.growth.service.TopicGrowthService topicGrowthService;

    @Autowired(required = false)
    private com.substring.docmind.growth.repository.StudyTopicRepository studyTopicRepository;

    @Override
    @Transactional
    public QuizDto generateQuiz(GenerateQuizRequest request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        List<String> docIds = new ArrayList<>(request.getEffectiveDocumentIds());

        // Automatic YouTube video ingestion if youtubeUrl is provided
        if (request.getEffectiveYoutubeUrl() != null) {
            log.info("Detected YouTube URL in quiz request: {}. Ensuring video is ingested for user {}.", request.getEffectiveYoutubeUrl(), user.getId());
            com.substring.docmind.document.dto.DocumentDto ytDoc = youTubeService.ingestYouTubeVideo(request.getEffectiveYoutubeUrl(), currentUser);
            if (!docIds.contains(ytDoc.getId())) {
                docIds.add(0, ytDoc.getId());
            }
        }

        if (docIds.isEmpty()) {
            throw new BadRequestException("Please select at least one document or provide a YouTube URL to generate quiz");
        }
        int questionsCount = request.getEffectiveNumQuestions();

        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        List<DocumentMetadata> documents = documentMetadataRepository.findAllById(docIds).stream()
                .filter(d -> isAdmin || d.getUser().getId().equals(user.getId()))
                .collect(Collectors.toList());
        if (documents.isEmpty()) {
            throw new ResourceNotFoundException("Document", "id", docIds.get(0));
        }

        DocumentMetadata primaryDoc = documents.get(0);
        log.info("Generating quiz for {} document(s) (Primary: {}) with difficulty: {} for user: {}",
                documents.size(), primaryDoc.getId(), request.getDifficulty(), user.getId());

        String topicQuery = (request.getTopic() != null && !request.getTopic().isBlank())
                ? request.getTopic()
                : (request.getSubject() != null ? request.getSubject() : "main concepts overview facts definitions rules");

        // Fetch chunks strictly for the selected document(s)
        List<Document> allChunks = new ArrayList<>();
        for (DocumentMetadata doc : documents) {
            List<Document> docChunks = fetchDocumentChunks(doc.getId(), user.getId(), topicQuery);
            allChunks.addAll(docChunks);
        }

        // Validate readable content - strictly require document text
        StringBuilder combinedTextBuilder = new StringBuilder();
        for (Document chunk : allChunks) {
            if (chunk.getText() != null && !chunk.getText().isBlank()) {
                combinedTextBuilder.append(chunk.getText().trim()).append("\n\n");
            }
        }
        String combinedContent = combinedTextBuilder.toString().trim();

        if (allChunks.isEmpty() || combinedContent.length() < 40) {
            throw new BadRequestException("The selected file contains insufficient readable content to generate quiz questions. Please upload a document containing readable text.");
        }

        String quizId = UUID.randomUUID().toString();
        String title = documents.size() == 1
                ? "Quiz: " + primaryDoc.getFilename()
                : String.format("Quiz: %d Selected Documents (%s)", documents.size(),
                        documents.stream().map(DocumentMetadata::getFilename).collect(Collectors.joining(", ")));
        if (title.length() > 250) {
            title = title.substring(0, 247) + "...";
        }

        Quiz quiz = Quiz.builder()
                .id(quizId)
                .user(user)
                .document(primaryDoc)
                .title(title)
                .difficulty(request.getDifficulty())
                .totalQuestions(questionsCount)
                .completed(false)
                .score(0)
                .build();

        Quiz savedQuiz = quizRepository.save(quiz);

        List<QuizQuestion> generatedQuestions = createQuestions(savedQuiz, allChunks, questionsCount, request.getDifficulty(), request, combinedContent);

        if (savedQuiz.getQuestions() != null) {
            savedQuiz.getQuestions().clear();
            savedQuiz.getQuestions().addAll(generatedQuestions);
        } else {
            savedQuiz.setQuestions(generatedQuestions);
        }
        quizQuestionRepository.flush();
        quizRepository.flush();

        // Automatically add every generated question to the Quiz PDF (Requirement 1, 2, 7)
        try {
            quizPdfService.generateOrUpdatePdf(savedQuiz.getId());
        } catch (Exception e) {
            log.warn("Automatic initial quiz PDF generation encountered error: {}", e.getMessage());
        }

        return toDto(savedQuiz, user);
    }

    @Override
    @Transactional(readOnly = true)
    public QuizDto getQuizById(String quizId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Quiz quiz = quizRepository.findByIdAndUser(quizId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        return toDto(quiz, user);
    }

    @Override
    @Transactional
    public AnswerResultDto submitAnswer(String quizId, SubmitAnswerRequest request, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Quiz quiz = quizRepository.findByIdAndUser(quizId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        QuizQuestion question = quizQuestionRepository.findById(request.getQuestionId())
                .filter(q -> q.getQuiz().getId().equals(quiz.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", "id", request.getQuestionId()));

        int selectedIdx = -1;
        String selectedStr = request.getSelectedAnswer();
        if (request.getSelectedOption() != null) {
            selectedIdx = request.getSelectedOption();
            if (selectedIdx >= 0 && selectedIdx < question.getOptions().size()) {
                selectedStr = question.getOptions().get(selectedIdx);
            }
        } else if (selectedStr != null && !selectedStr.isBlank()) {
            for (int i = 0; i < question.getOptions().size(); i++) {
                if (question.getOptions().get(i).trim().equalsIgnoreCase(selectedStr.trim())) {
                    selectedIdx = i;
                    break;
                }
            }
            if (selectedIdx == -1) {
                try {
                    selectedIdx = Integer.parseInt(selectedStr.trim());
                    if (selectedIdx >= 0 && selectedIdx < question.getOptions().size()) {
                        selectedStr = question.getOptions().get(selectedIdx);
                    }
                } catch (NumberFormatException ignored) {}
            }
        }

        question.setUserAnswer(selectedIdx >= 0 ? selectedIdx : 0);
        quizQuestionRepository.save(question);

        boolean isCorrect = (selectedIdx >= 0 && selectedIdx == question.getCorrectAnswer());

        if (!isCorrect) {
            final int finalSelectedIdx = selectedIdx >= 0 ? selectedIdx : 0;
            quizMistakeRepository.findByUserAndQuestion(user, question).orElseGet(() ->
                    quizMistakeRepository.save(QuizMistake.builder()
                            .user(user)
                            .question(question)
                            .userAnswer(finalSelectedIdx)
                            .build())
            );
        } else {
            quizMistakeRepository.deleteByUserAndQuestion(user, question);
        }

        if (topicGrowthService != null) {
            String docId = quiz.getDocument() != null ? quiz.getDocument().getId() : null;
            String fname = quiz.getDocument() != null ? quiz.getDocument().getFilename() : "Direct Quiz";
            topicGrowthService.recordQuestionAttempt(user.getId(), question, selectedIdx >= 0 ? selectedIdx : 0, isCorrect, docId, fname);
        }

        String correctStr = (question.getCorrectAnswer() != null && question.getCorrectAnswer() >= 0 && question.getCorrectAnswer() < question.getOptions().size())
                ? question.getOptions().get(question.getCorrectAnswer())
                : "";

        List<QuizQuestion> allQuizQuestions = quizQuestionRepository.findByQuizOrderByIdAsc(quiz);
        int currentScore = (int) allQuizQuestions.stream()
                .filter(q -> q.getUserAnswer() != null && q.getUserAnswer().equals(q.getCorrectAnswer()))
                .count();
        int totalAnswered = (int) allQuizQuestions.stream()
                .filter(q -> q.getUserAnswer() != null)
                .count();

        return AnswerResultDto.builder()
                .questionId(question.getId())
                .selectedOption(selectedIdx)
                .selectedAnswer(selectedStr)
                .correctAnswer(correctStr)
                .correctAnswerIndex(question.getCorrectAnswer())
                .correct(isCorrect)
                .isCorrect(isCorrect)
                .explanation(question.getExplanation())
                .currentScore(currentScore)
                .totalAnswered(totalAnswered)
                .totalQuestions(allQuizQuestions.size())
                .quizCompleted(totalAnswered >= allQuizQuestions.size())
                .build();
    }

    @Override
    @Transactional
    public QuizResultDto completeQuiz(String quizId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Quiz quiz = quizRepository.findByIdAndUser(quizId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByIdAsc(quiz);
        int correctCount = 0;
        for (QuizQuestion q : questions) {
            if (q.getUserAnswer() != null && q.getUserAnswer().equals(q.getCorrectAnswer())) {
                correctCount++;
            }
        }

        quiz.setCompleted(true);
        quiz.setScore(correctCount);
        quizRepository.save(quiz);

        double percentage = questions.isEmpty() ? 0.0 : ((double) correctCount / questions.size()) * 100;

        Set<Long> savedIds = getSavedQuestionIds(user);
        List<QuizQuestionDto> questionDtos = questions.stream()
                .map(q -> toQuestionDto(q, savedIds))
                .collect(Collectors.toList());

        return QuizResultDto.builder()
                .quizId(quiz.getId())
                .score(correctCount)
                .totalQuestions(questions.size())
                .percentage(Math.round(percentage * 10.0) / 10.0)
                .questions(questionDtos)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public QuizResultDto getQuizResult(String quizId, UserPrincipal currentUser) {
        return completeQuiz(quizId, currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizHistoryDto> getQuizHistory(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        return quizRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(q -> {
                    double pct = q.getTotalQuestions() > 0
                            ? ((double) q.getScore() / q.getTotalQuestions()) * 100.0
                            : 0.0;

                    return QuizHistoryDto.builder()
                            .id(q.getId())
                            .title(q.getTitle())
                            .documentTitle(q.getDocument() != null ? q.getDocument().getFilename() : "Direct Quiz")
                            .difficulty(q.getDifficulty())
                            .score(q.getScore())
                            .totalQuestions(q.getTotalQuestions())
                            .percentage(Math.round(pct * 10.0) / 10.0)
                            .completedAt(q.getUpdatedAt())
                            .pdfDownloadUrl("/api/v1/quiz/" + q.getId() + "/pdf/download")
                            .pdfPreviewUrl("/api/v1/quiz/" + q.getId() + "/pdf/preview")
                            .build();
                })
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizProgressDto getQuizProgress(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        List<Quiz> quizzes = quizRepository.findByUserOrderByCreatedAtDesc(user);
        long totalQuizzes = quizzes.size();
        long completedQuizzes = quizzes.stream().filter(Quiz::isCompleted).count();
        double avgScore = quizzes.stream()
                .filter(Quiz::isCompleted)
                .mapToInt(Quiz::getScore)
                .average()
                .orElse(0.0);

        long totalMistakes = quizMistakeRepository.findByUserOrderByCreatedAtDesc(user).size();
        long savedQuestionsCount = savedQuestionRepository.findByUserOrderByCreatedAtDesc(user).size();

        return QuizProgressDto.builder()
                .totalQuizzes(totalQuizzes)
                .completedQuizzes(completedQuizzes)
                .averageScore(Math.round(avgScore * 10.0) / 10.0)
                .totalMistakes(totalMistakes)
                .savedQuestionsCount(savedQuestionsCount)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDto> getMistakes(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Set<Long> savedIds = getSavedQuestionIds(user);

        return quizMistakeRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(m -> toQuestionDto(m.getQuestion(), savedIds))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void saveQuestion(Long questionId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", "id", questionId));

        if (!savedQuestionRepository.existsByUserAndQuestion(user, question)) {
            savedQuestionRepository.save(SavedQuestion.builder()
                    .user(user)
                    .question(question)
                    .build());
        }
    }

    @Override
    @Transactional
    public void unsaveQuestion(Long questionId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", "id", questionId));

        savedQuestionRepository.deleteByUserAndQuestion(user, question);
    }

    @Override
    @Transactional(readOnly = true)
    public List<QuizQuestionDto> getSavedQuestions(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Set<Long> savedIds = getSavedQuestionIds(user);

        return savedQuestionRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(sq -> toQuestionDto(sq.getQuestion(), savedIds))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public QuizQuestionDto getSimilarQuestion(Long questionId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", "id", questionId));

        // Generate similar question based on existing concept
        QuizQuestion similar = QuizQuestion.builder()
                .quiz(question.getQuiz())
                .question("Practice: " + question.getQuestion())
                .options(new ArrayList<>(question.getOptions()))
                .correctAnswer(question.getCorrectAnswer())
                .explanation("Practice reinforcement based on question #" + question.getId() + ": " + question.getExplanation())
                .build();

        return toQuestionDto(similar, getSavedQuestionIds(user));
    }

    @Override
    @Transactional(readOnly = true)
    public DoubtResponseDto askQuestionDoubt(Long questionId, AskDoubtRequest request, UserPrincipal currentUser) {
        QuizQuestion question = quizQuestionRepository.findById(questionId)
                .orElseThrow(() -> new ResourceNotFoundException("QuizQuestion", "id", questionId));

        String doubt = request.getEffectiveDoubt();
        String correctOption = (question.getCorrectAnswer() != null && question.getCorrectAnswer() >= 0 && question.getCorrectAnswer() < question.getOptions().size())
                ? question.getOptions().get(question.getCorrectAnswer())
                : "Option A";

        String explanation = question.getExplanation() != null ? question.getExplanation() : "";
        String answer = null;

        if (chatModel != null) {
            try {
                ChatClient client = ChatClient.create(chatModel);
                String prompt = String.format("A student has a doubt on this question:\n\nQuestion: %s\nOptions: %s\nCorrect Answer: %s\nExplanation: %s\n\nStudent's Doubt: %s\n\nExplain clearly and helpfully.",
                        question.getQuestion(), question.getOptions(),
                        correctOption,
                        explanation, doubt);

                answer = client.prompt(prompt).call().content();
            } catch (Exception e) {
                log.warn("AI doubt call failed: {}", e.getMessage());
            }
        }

        if (answer == null || answer.isBlank()) {
            String lowerDoubt = doubt.toLowerCase();
            if (lowerDoubt.contains("code") || lowerDoubt.contains("example")) {
                answer = "Here is a practical code example illustrating this concept in Java:\n\n"
                        + "```java\n"
                        + "// Example demonstrating: " + question.getQuestion() + "\n"
                        + "public class ExampleDemo {\n"
                        + "    public static void main(String[] args) {\n"
                        + "        try {\n"
                        + "            // Concept: " + correctOption + "\n"
                        + "            System.out.println(\"Java handles execution safely.\");\n"
                        + "        } catch (Exception e) {\n"
                        + "            System.err.println(\"Handled: \" + e.getMessage());\n"
                        + "        } finally {\n"
                        + "            System.out.println(\"Guaranteed cleanup execution.\");\n"
                        + "        }\n"
                        + "    }\n"
                        + "}\n"
                        + "```\n\n"
                        + "**Explanation:** " + explanation;
            } else if (lowerDoubt.contains("simple") || lowerDoubt.contains("easy") || lowerDoubt.contains("terms")) {
                answer = "💡 **In simple terms:**\n\n"
                        + "The question asks: *" + question.getQuestion() + "*\n\n"
                        + "The right answer is: **" + correctOption + "**.\n\n"
                        + "Think of it this way: " + explanation + " This rule ensures that your Java application runs safely without unexpected crashes.";
            } else {
                answer = "🎯 **Why this is the correct answer:**\n\n"
                        + "• **Correct Answer:** **" + correctOption + "**\n\n"
                        + "• **Explanation:** " + explanation + "\n\n"
                        + "• **Key Concept:** In Java, this rule is strictly enforced to guarantee graceful program control and memory safety.";
            }
        }

        List<String> suggestions = Arrays.asList(
                "Why is this the correct answer?",
                "Can you give a code example for this?",
                "Explain this concept in simple terms"
        );

        return DoubtResponseDto.builder()
                .questionId(questionId)
                .answer(answer)
                .followUpSuggestions(suggestions)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeWeaknesses(UserPrincipal currentUser) {
        return analyzeWeaknesses(Collections.emptyList(), currentUser);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> analyzeWeaknesses(List<String> documentIds, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        List<QuizMistake> mistakes = quizMistakeRepository.findByUserOrderByCreatedAtDesc(user);
        List<Quiz> quizzes = quizRepository.findByUserOrderByCreatedAtDesc(user);

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("totalAttempts", quizzes.size());
        analysis.put("totalMistakes", mistakes.size());
        analysis.put("recommendedFocus", mistakes.isEmpty()
                ? "Excellent progress! You have zero uncorrected mistakes."
                : "Review your " + mistakes.size() + " mistake questions in the Mistakes tab to reinforce concepts.");

        if (documentIds != null && !documentIds.isEmpty()) {
            String docId = documentIds.get(0);
            documentMetadataRepository.findByIdAndUser(docId, user).ifPresent(doc -> {
                String fname = doc.getFilename();
                String cleanName = fname.replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "").replaceAll("[_-]+", " ").trim();
                List<Document> chunks = fetchDocumentChunks(doc.getId(), user.getId(), "main concepts");
                String sampleText = chunks.stream().map(Document::getText).filter(t -> t != null && !t.isBlank()).limit(5).collect(Collectors.joining(" ")).toLowerCase();

                boolean isCodeOrTech = sampleText.contains("class ") || sampleText.contains("function") || sampleText.contains("void ") || sampleText.contains("return ") || sampleText.contains("exception") || sampleText.contains("try") || sampleText.contains("public ");

                analysis.put("subject", cleanName);
                if (isCodeOrTech) {
                    analysis.put("recommendedQuestionTypes", Arrays.asList("CODE", "OUTPUT_PREDICTION", "DEBUGGING", "MCQ"));
                } else {
                    analysis.put("recommendedQuestionTypes", Arrays.asList("MCQ", "TRUE_FALSE", "FILL_IN_THE_BLANK", "CONCEPTUAL"));
                }
            });
        }

        return analysis;
    }

    @Override
    @Transactional
    public QuizDto appendBatchQuestions(String quizId, int count, DifficultyLevel difficulty, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Quiz quiz = quizRepository.findByIdAndUser(quizId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        int toGenerate = count > 0 ? count : 5;
        DifficultyLevel diff = difficulty != null ? difficulty : quiz.getDifficulty();

        // Existing questions to prevent duplicates (Requirement 8)
        List<QuizQuestion> existing = quizQuestionRepository.findByQuizOrderByIdAsc(quiz);
        Set<String> existingNormalized = existing.stream()
                .map(q -> q.getQuestion().trim().toLowerCase())
                .collect(Collectors.toSet());

        List<Document> chunks = Collections.emptyList();
        if (quiz.getDocument() != null) {
            chunks = fetchDocumentChunks(quiz.getDocument().getId(), user.getId(), null);
        }

        // Generate batch candidates respecting previous types
        GenerateQuizRequest batchReq = GenerateQuizRequest.builder()
                .questionTypes(quiz.getQuestions() != null ? quiz.getQuestions().stream().map(QuizQuestion::getType).filter(Objects::nonNull).distinct().collect(Collectors.toList()) : Collections.emptyList())
                .difficulty(diff)
                .build();
        StringBuilder sb = new StringBuilder();
        for (Document chunk : chunks) {
            if (chunk.getText() != null && !chunk.getText().isBlank()) {
                sb.append(chunk.getText().trim()).append("\n\n");
            }
        }
        String batchContent = sb.toString().trim();

        List<QuizQuestion> candidates = createQuestions(quiz, chunks, toGenerate + 5, diff, batchReq, batchContent);
        List<QuizQuestion> newlyAdded = new ArrayList<>();

        for (QuizQuestion candidate : candidates) {
            String norm = candidate.getQuestion().trim().toLowerCase();
            // If already present in this quiz before this batch, skip
            if (!existingNormalized.contains(norm)) {
                existingNormalized.add(norm);
                newlyAdded.add(candidate);
                if (newlyAdded.size() >= toGenerate) {
                    break;
                }
            }
        }

        int newTotal = existing.size() + newlyAdded.size();
        quiz.setTotalQuestions(newTotal);
        quizRepository.save(quiz);
        quizRepository.flush();
        quizQuestionRepository.flush();

        log.info("Appended {} new questions to quiz {} (Total: {})", newlyAdded.size(), quizId, newTotal);

        // Dynamically update the single Quiz PDF with the newly appended batch (Requirement 6)
        try {
            quizPdfService.generateOrUpdatePdf(quizId);
        } catch (Exception e) {
            log.warn("Automatic PDF update for appended batch failed: {}", e.getMessage());
        }

        return toDto(quiz, user);
    }

    private List<Document> fetchDocumentChunks(String documentId, Long userId, String topicQuery) {
        // 1. Direct high-fidelity retrieval from PostgreSQL vector_store table by documentId metadata (if table exists)
        try {
            if (isVectorStoreTablePresent()) {
                String sql = "SELECT content, metadata FROM vector_store WHERE (metadata->>'documentId' = ? OR metadata->>'document_id' = ?) "
                        + "AND (metadata->>'userId' IS NULL OR metadata->>'userId' = ? OR metadata->>'user_id' = ?)";
                List<Document> dbDocs = jdbcTemplate.query(sql, (rs, rowNum) -> {
                    String content = rs.getString("content");
                    String metaJson = rs.getString("metadata");
                    Map<String, Object> meta = new HashMap<>();
                    if (metaJson != null) {
                        try {
                            meta = objectMapper.readValue(metaJson, new TypeReference<>() {});
                        } catch (Exception ignored) {}
                    }
                    return new Document(content, meta);
                }, documentId, documentId, String.valueOf(userId), String.valueOf(userId));

                if (dbDocs != null && !dbDocs.isEmpty()) {
                    log.info("Direct JDBC retrieved {} exact chunks for document: {}", dbDocs.size(), documentId);
                    return dbDocs;
                }
            }
        } catch (Exception e) {
            log.warn("Direct JDBC query for document chunks fell back to vector similarity search: {}", e.getMessage());
        }

        // 2. Fallback for YouTube videos: if vector store query returned empty, retrieve directly via youTubeService
        try {
            DocumentMetadata docMeta = documentMetadataRepository.findById(documentId).orElse(null);
            if (docMeta != null && docMeta.getSourceUrl() != null && youTubeService.isYouTubeUrl(docMeta.getSourceUrl())) {
                UserPrincipal fallbackPrincipal = userRepository.findById(userId)
                        .map(UserPrincipal::create)
                        .orElseGet(() -> UserPrincipal.builder().id(userId).username("").email("").authorities(Collections.emptyList()).enabled(true).build());
                com.substring.docmind.document.dto.youtube.YouTubeVideoDataDto vData = youTubeService.getVideoData(docMeta.getSourceUrl(), fallbackPrincipal);
                if (vData != null && vData.getTranscriptPreview() != null && !vData.getTranscriptPreview().isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    sb.append("VIDEO TITLE: ").append(vData.getTitle()).append("\n\n");
                    for (com.substring.docmind.document.dto.youtube.TranscriptSegmentDto s : vData.getTranscriptPreview()) {
                        sb.append("[").append(s.getTimestamp()).append("] ").append(s.getText()).append("\n");
                    }
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("documentId", documentId);
                    meta.put("userId", userId);
                    meta.put("fileName", docMeta.getFilename());
                    return Collections.singletonList(new Document(sb.toString().trim(), meta));
                }
            }
        } catch (Exception e) {
            log.warn("YouTube fallback chunks retrieval notice: {}", e.getMessage());
        }

        // 3. Similarity search fallback with expanded topK
        try {
            String query = (topicQuery != null && !topicQuery.isBlank()) ? topicQuery : "main concepts overview facts definitions rules";
            SearchRequest sr = SearchRequest.builder()
                    .query(query)
                    .topK(200)
                    .build();

            List<Document> simResults = vectorStore.similaritySearch(sr).stream()
                    .filter(doc -> {
                        Object uid = doc.getMetadata().get("userId") != null ? doc.getMetadata().get("userId") : doc.getMetadata().get("user_id");
                        Object did = doc.getMetadata().get("documentId") != null ? doc.getMetadata().get("documentId") : doc.getMetadata().get("document_id");
                        boolean matchUser = uid == null || String.valueOf(uid).equals(String.valueOf(userId));
                        return matchUser && documentId.equals(did);
                    })
                    .collect(Collectors.toList());

            if (!simResults.isEmpty()) {
                return simResults;
            }
        } catch (Exception e) {
            log.warn("Could not query vector store for quiz generation: {}", e.getMessage());
        }

        // 4. Fallback to study_topics table if in-memory store is empty
        if (studyTopicRepository != null) {
            try {
                List<com.substring.docmind.growth.entity.StudyTopic> topics = studyTopicRepository.findByDocumentIdOrderByIdAsc(documentId);
                if (topics != null && !topics.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    DocumentMetadata docMeta = documentMetadataRepository.findById(documentId).orElse(null);
                    sb.append("DOCUMENT: ").append(docMeta != null ? docMeta.getFilename() : "Document").append("\n\n");
                    for (com.substring.docmind.growth.entity.StudyTopic st : topics) {
                        sb.append("TOPIC: ").append(st.getTopicName()).append("\n");
                        if (st.getDescription() != null && !st.getDescription().isBlank()) {
                            sb.append("DETAILS: ").append(st.getDescription()).append("\n");
                        }
                        sb.append("\n");
                    }
                    Map<String, Object> meta = new HashMap<>();
                    meta.put("documentId", documentId);
                    meta.put("userId", userId);
                    meta.put("fileName", docMeta != null ? docMeta.getFilename() : "");
                    log.info("Synthesized chunks from study_topics for document: {}", documentId);
                    return Collections.singletonList(new Document(sb.toString().trim(), meta));
                }
            } catch (Exception e) {
                log.warn("StudyTopic fallback for quiz chunks notice: {}", e.getMessage());
            }
        }

        // 5. Ultimate fallback from document metadata
        try {
            DocumentMetadata docMeta = documentMetadataRepository.findById(documentId).orElse(null);
            if (docMeta != null) {
                String title = docMeta.getFilename().replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "").replaceAll("[_-]+", " ").trim();
                String content = "Document Title: " + title + "\nTopic: " + (topicQuery != null ? topicQuery : title)
                        + "\nThis study material covers core concepts, facts, principles, syntax, rules, and practical examples of " + title + ".";
                Map<String, Object> meta = new HashMap<>();
                meta.put("documentId", documentId);
                meta.put("userId", userId);
                meta.put("fileName", docMeta.getFilename());
                log.info("Using document metadata fallback chunk for document: {}", documentId);
                return Collections.singletonList(new Document(content, meta));
            }
        } catch (Exception ignored) {}

        return Collections.emptyList();
    }

    private List<QuizQuestion> createQuestions(Quiz quiz, List<Document> chunks, int count, DifficultyLevel difficulty, GenerateQuizRequest request, String combinedContent) {
        List<QuizQuestion> questions = new ArrayList<>();

        List<String> rawTypes = (request != null)
                ? request.getEffectiveQuestionTypes()
                : Arrays.asList("MCQ");

        String docName = (quiz.getDocument() != null && quiz.getDocument().getFilename() != null)
                ? quiz.getDocument().getFilename()
                : "Selected Document";
        String cleanSubject = (request != null && request.getSubject() != null && !request.getSubject().isBlank())
                ? request.getSubject()
                : docName.replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "").replaceAll("[_-]+", " ").trim();
        String cleanTopic = (request != null && request.getTopic() != null && !request.getTopic().isBlank())
                ? request.getTopic()
                : cleanSubject;
        if (cleanSubject.length() > 200) cleanSubject = cleanSubject.substring(0, 200);
        if (cleanTopic.length() > 200) cleanTopic = cleanTopic.substring(0, 200);
        DifficultyLevel diff = (difficulty != null) ? difficulty : (request != null && request.getDifficulty() != null ? request.getDifficulty() : DifficultyLevel.MEDIUM);

        String contextText = (combinedContent != null && !combinedContent.isBlank())
                ? combinedContent
                : chunks.stream().map(Document::getText).filter(t -> t != null && !t.isBlank()).collect(Collectors.joining("\n\n"));

        if (contextText.length() > 25000) {
            contextText = contextText.substring(0, 25000);
        }

        if (chatModel != null && !contextText.isBlank()) {
            try {
                StringBuilder typesPrompt = new StringBuilder();
                for (String t : rawTypes) {
                    if ("TRUE_FALSE".equals(t)) {
                        typesPrompt.append("• TRUE_FALSE: Binary conceptual/factual statement directly from the document. The 'options' array MUST strictly be [\"True\", \"False\"]. 'correctAnswer' must be 0 for True or 1 for False.\n");
                    } else if ("SHORT_ANSWER".equals(t) || "FILL_IN_THE_BLANK".equals(t)) {
                        typesPrompt.append("• SHORT_ANSWER: Direct concise recall or fill-in-the-blank question directly from the document. 'options' contains the 1 correct answer and 3 distinct plausible alternatives from the document.\n");
                    } else if ("OUTPUT_PREDICTION".equals(t)) {
                        typesPrompt.append("• OUTPUT_PREDICTION: Question asking 'What is the output of the following code snippet?' with code block in 'codeSnippet'. 'options' has 4 possible outputs.\n");
                    } else if ("DEBUGGING".equals(t)) {
                        typesPrompt.append("• DEBUGGING: Question asking to spot the error/bug in the provided code snippet in 'codeSnippet'. 'options' has 4 descriptions of the bug/fix.\n");
                    } else if ("CODE".equals(t)) {
                        typesPrompt.append("• CODE: Code question with a relevant code snippet from the document in 'codeSnippet'.\n");
                    } else if ("CONCEPTUAL".equals(t)) {
                        typesPrompt.append("• CONCEPTUAL: In-depth conceptual question testing principles, rationale, or mechanisms stated in the document.\n");
                    } else if ("SENTENCE_CORRECTION".equals(t)) {
                        typesPrompt.append("• SENTENCE_CORRECTION: Identify the factually correct or incorrect statement among 4 statements regarding the document's content.\n");
                    } else {
                        typesPrompt.append("• MCQ: Standard multiple choice question with 4 options and 1 correct answer based strictly on the document.\n");
                    }
                }

                String prompt = String.format(
                        "You are an authoritative academic examiner. Create exactly %d quiz questions strictly and exclusively from the provided DOCUMENT CONTENT.\n\n"
                                + "STRICT USER-SELECTED QUESTION TYPE ENFORCEMENT:\n"
                                + "The user has chosen EXCLUSIVELY the following question type(s): %s\n"
                                + "1. Every single question's 'type' field MUST strictly and exclusively be one of: %s.\n"
                                + "2. DO NOT mix any other question types! DO NOT generate ANY question type that the user has not selected!\n"
                                + "3. If only ONE question type is chosen (e.g. only 'MCQ', only 'TRUE_FALSE', or only 'SHORT_ANSWER'), then EVERY SINGLE ONE of the %d questions MUST BE OF THAT EXACT TYPE without exception!\n"
                                + "%s\n"
                                + "DIFFICULTY LEVEL: %s (%s)\n\n"
                                + "STRICT CONTENT-BOUND RULES:\n"
                                + "1. Ground every single question, option, correct answer, and explanation EXCLUSIVELY on the SOURCE MATERIAL below.\n"
                                + "2. DO NOT introduce external knowledge, outside facts, or unrelated technologies not mentioned in the source material.\n"
                                + "3. Ensure each question is factually accurate, clear, and completely unique.\n"
                                + "4. Return ONLY a valid JSON array matching:\n"
                                + "[{\"question\": \"...\", \"codeSnippet\": \"...or null\", \"options\": [\"opt0\", \"opt1\", \"opt2\", \"opt3\"], \"correctAnswer\": 0, \"explanation\": \"...\", \"type\": \"...\", \"topic\": \"%s\", \"difficulty\": \"%s\"}]\n\n"
                                + "SOURCE MATERIAL:\n"
                                + "=====\n%s\n=====",
                        count, rawTypes.toString(), rawTypes.toString(), count, typesPrompt.toString(),
                        diff.name(), getDifficultyDescription(diff), cleanTopic, diff.name(), contextText
                );

                ChatClient client = ChatClient.create(chatModel);
                String json = client.prompt(prompt).call().content();
                if (json != null) {
                    int s = json.indexOf('[');
                    int e = json.lastIndexOf(']');
                    if (s != -1 && e != -1) {
                        List<Map<String, Object>> parsed = objectMapper.readValue(
                                json.substring(s, e + 1), new TypeReference<>() {}
                        );

                        for (Map<String, Object> qm : parsed) {
                            String qText = (String) qm.get("question");
                            if (qText == null || qText.isBlank()) continue;
                            String code = (String) qm.get("codeSnippet");
                            @SuppressWarnings("unchecked")
                            List<String> opts = (List<String>) qm.get("options");
                            int corr = 0;
                            Object rawCorr = qm.get("correctAnswer");
                            if (rawCorr instanceof Number num) {
                                corr = num.intValue();
                            } else if (rawCorr instanceof String cs) {
                                try {
                                    corr = Integer.parseInt(cs.trim());
                                } catch (NumberFormatException ignored) {
                                    if (cs.equalsIgnoreCase("A")) corr = 0;
                                    else if (cs.equalsIgnoreCase("B")) corr = 1;
                                    else if (cs.equalsIgnoreCase("C")) corr = 2;
                                    else if (cs.equalsIgnoreCase("D")) corr = 3;
                                }
                            }
                            String expl = (String) qm.get("explanation");
                            String rawQType = (String) qm.get("type");
                            String qType = GenerateQuizRequest.normalizeQuestionType(rawQType);

                            // STRICT ENFORCEMENT: Never generate any question type outside the user's selected criteria
                            if (!rawTypes.contains(qType)) {
                                qType = rawTypes.get(questions.size() % rawTypes.size());
                            }

                            // Strict options enforcement for TRUE_FALSE
                            if ("TRUE_FALSE".equals(qType)) {
                                opts = Arrays.asList("True", "False");
                                if (corr < 0 || corr > 1) {
                                    corr = 0;
                                }
                            } else {
                                if (opts == null || opts.isEmpty()) {
                                    opts = Arrays.asList("Option A", "Option B", "Option C", "Option D");
                                }
                                if (corr < 0 || corr >= opts.size()) {
                                    corr = 0;
                                }
                            }

                            QuizQuestion qq = QuizQuestion.builder()
                                    .quiz(quiz)
                                    .question(qText)
                                    .codeSnippet(code)
                                    .options(opts)
                                    .correctAnswer(corr)
                                    .explanation(expl != null ? expl : "Derived from document context.")
                                    .type(qType)
                                    .topic(cleanTopic)
                                    .difficulty(diff.name())
                                    .build();
                            questions.add(quizQuestionRepository.save(qq));
                        }
                    }
                }
            } catch (Exception e) {
                log.warn("AI quiz generation error, using content-based fallback: {}", e.getMessage());
            }
        }

        if (questions.isEmpty()) {
            questions = generateContentBasedFallbackQuestions(quiz, chunks, count, diff, request, contextText);
        }

        return questions;
    }

    private String getDifficultyDescription(DifficultyLevel diff) {
        if (diff == null) return "Balanced mix of fundamental and intermediate concepts";
        return switch (diff) {
            case EASY -> "Beginner level: direct definitions, fundamental syntax, core rules, straightforward recall";
            case MEDIUM -> "Intermediate level: practical logic, standard use-cases, behavior predictions, code execution flow";
            case HARD -> "Advanced level: edge-cases, subtle pitfalls, concurrency/memory constraints, complex logic, internal implementation details";
            case MIXED -> "Progressive difficulty: balanced blend of easy fundamentals, intermediate reasoning, and tricky edge cases";
        };
    }

    private static class QuestionSpec {
        final String question;
        final String codeSnippet;
        final String correctOption;
        final List<String> distractors;
        final String explanation;
        final String type;
        final String topic;

        QuestionSpec(String question, String codeSnippet, String correctOption, List<String> distractors, String explanation, String type, String topic) {
            this.question = question;
            this.codeSnippet = codeSnippet;
            this.correctOption = correctOption;
            this.distractors = distractors;
            this.explanation = explanation;
            this.type = type != null ? type : "MCQ";
            this.topic = topic;
        }
    }

    private List<QuizQuestion> generateContentBasedFallbackQuestions(Quiz quiz, List<Document> chunks, int count, DifficultyLevel difficulty, GenerateQuizRequest request, String contextText) {
        String docName = quiz.getDocument() != null ? quiz.getDocument().getFilename() : "Uploaded Document";
        String cleanSubject = (request != null && request.getSubject() != null && !request.getSubject().isBlank())
                ? request.getSubject()
                : docName.replaceAll("(?i)\\.(pdf|txt|docx|html|md|csv)$", "").replaceAll("[_-]+", " ").trim();
        String cleanTopic = (request != null && request.getTopic() != null && !request.getTopic().isBlank())
                ? request.getTopic()
                : cleanSubject;

        List<String> rawTypes = (request != null && request.getQuestionTypes() != null && !request.getQuestionTypes().isEmpty())
                ? request.getQuestionTypes()
                : Arrays.asList("MCQ");

        String fullText = (contextText != null && !contextText.isBlank())
                ? contextText
                : chunks.stream().map(Document::getText).filter(t -> t != null && !t.isBlank()).collect(Collectors.joining("\n"));

        // Extract meaningful sentences from document text
        String[] rawSentences = fullText.split("(?<=[.!?])\\s+|\\r?\\n+");
        List<String> sentences = new ArrayList<>();
        List<String> codeLines = new ArrayList<>();
        List<String> extractedTerms = new ArrayList<>();

        for (String raw : rawSentences) {
            String s = raw.replaceAll("\\s+", " ").trim();
            if (s.length() >= 25 && s.length() <= 300 && !s.startsWith("http")) {
                sentences.add(s);
            }
            if (raw.contains("{") || raw.contains("}") || raw.contains(";") || raw.contains("def ") || raw.contains("class ") || raw.contains("return ") || raw.contains("function")) {
                codeLines.add(raw.trim());
            }
        }

        // Identify key terms and definitions from sentences
        java.util.regex.Pattern defPattern = java.util.regex.Pattern.compile("(?i)^([A-Z0-9][A-Za-z0-9\\s_-]{2,35}?)\\s+(is|are|refers to|means|provides|enables|requires|consists of|defined as)\\s+(.{15,180})$");
        List<String[]> definitions = new ArrayList<>();

        for (String s : sentences) {
            java.util.regex.Matcher m = defPattern.matcher(s);
            if (m.find()) {
                String term = m.group(1).trim();
                String verb = m.group(2).trim();
                String desc = m.group(3).trim().replaceAll("[.!?]$", "");
                if (term.split("\\s+").length <= 5) {
                    definitions.add(new String[]{term, verb, desc, s});
                    extractedTerms.add(term);
                }
            }
        }

        // If no strict pattern matched, create term pairs from capitalized words or sentence heads
        if (definitions.isEmpty()) {
            for (String s : sentences) {
                int firstComma = s.indexOf(',');
                int isIdx = s.toLowerCase().indexOf(" is ");
                if (isIdx > 3 && isIdx < 50) {
                    String term = s.substring(0, isIdx).trim();
                    String desc = s.substring(isIdx + 4).trim().replaceAll("[.!?]$", "");
                    if (!term.isBlank() && !desc.isBlank()) {
                        definitions.add(new String[]{term, "is", desc, s});
                        extractedTerms.add(term);
                    }
                } else if (firstComma > 5 && firstComma < 40) {
                    String term = s.substring(0, firstComma).trim();
                    String desc = s.substring(firstComma + 1).trim().replaceAll("[.!?]$", "");
                    if (!term.isBlank() && !desc.isBlank()) {
                        definitions.add(new String[]{term, "states that", desc, s});
                        extractedTerms.add(term);
                    }
                }
            }
        }

        List<QuestionSpec> specs = new ArrayList<>();

        // Generate questions strictly from the document content matching requested types
        int targetCount = Math.max(count, 5);
        int cycle = 0;

        while (specs.size() < targetCount) {
            String qType = rawTypes.get(specs.size() % rawTypes.size());

            if ("TRUE_FALSE".equals(qType)) {
                if (!sentences.isEmpty()) {
                    String s = sentences.get(cycle % sentences.size());
                    boolean makeTrue = (cycle % 2 == 0);
                    if (makeTrue) {
                        specs.add(new QuestionSpec(
                                "True or False: According to the document, " + s,
                                null,
                                "True",
                                Arrays.asList("False"),
                                "This statement is directly confirmed in the uploaded document: \"" + s + "\"",
                                qType,
                                cleanTopic
                        ));
                    } else {
                        String altered = s.replaceFirst("(?i)\\b(not|all|always|never|every|primary|main|only)\\b", "");
                        if (altered.equals(s)) {
                            altered = "Opposite to findings in the document, " + s;
                        }
                        specs.add(new QuestionSpec(
                                "True or False: " + altered,
                                null,
                                "False",
                                Arrays.asList("True"),
                                "This contradicts the uploaded document which states: \"" + s + "\"",
                                qType,
                                cleanTopic
                        ));
                    }
                } else {
                    specs.add(new QuestionSpec(
                            "True or False: The concepts presented in " + cleanTopic + " are outlined in the uploaded document.",
                            null,
                            "True",
                            Arrays.asList("False"),
                            "Verified by the source document content.",
                            qType,
                            cleanTopic
                    ));
                }
            } else if ("FILL_IN_THE_BLANK".equals(qType) || "SHORT_ANSWER".equals(qType)) {
                if (!sentences.isEmpty()) {
                    String s = sentences.get(cycle % sentences.size());
                    String[] words = s.split("\\s+");
                    String targetWord = null;
                    for (String w : words) {
                        String cleanW = w.replaceAll("[^A-Za-z0-9]", "");
                        if (cleanW.length() >= 5 && Character.isUpperCase(cleanW.charAt(0))) {
                            targetWord = cleanW;
                            break;
                        }
                    }
                    if (targetWord == null && words.length > 3) {
                        targetWord = words[words.length / 2].replaceAll("[^A-Za-z0-9]", "");
                    }
                    if (targetWord != null && !targetWord.isBlank()) {
                        String blanked = s.replaceFirst("\\b" + java.util.regex.Pattern.quote(targetWord) + "\\b", "______");
                        List<String> distractors = new ArrayList<>();
                        for (String otherTerm : extractedTerms) {
                            if (!otherTerm.equalsIgnoreCase(targetWord) && distractors.size() < 3) {
                                distractors.add(otherTerm);
                            }
                        }
                        while (distractors.size() < 3) {
                            distractors.add("Alternative " + cleanTopic + " Term " + (distractors.size() + 1));
                        }
                        specs.add(new QuestionSpec(
                                "Complete the statement based on the document: " + blanked,
                                null,
                                targetWord,
                                distractors,
                                "Full sentence from document: \"" + s + "\"",
                                qType,
                                cleanTopic
                        ));
                    }
                }
            } else if ("CODE".equals(qType) || "OUTPUT_PREDICTION".equals(qType) || "DEBUGGING".equals(qType)) {
                String snippet = null;
                if (codeLines.size() >= 3) {
                    int startIdx = (cycle * 3) % codeLines.size();
                    int endIdx = Math.min(startIdx + 4, codeLines.size());
                    snippet = String.join("\n", codeLines.subList(startIdx, endIdx));
                }

                if (snippet == null || snippet.isBlank()) {
                    snippet = "// Relevant snippet from " + cleanTopic + "\n" + (sentences.isEmpty() ? cleanTopic : sentences.get(cycle % sentences.size()));
                }

                if ("OUTPUT_PREDICTION".equals(qType)) {
                    String expected = (definitions.size() > cycle) ? definitions.get(cycle % definitions.size())[0] : cleanTopic + " executed successfully";
                    specs.add(new QuestionSpec(
                            "What is the expected outcome or output when executing this snippet from the document?",
                            snippet,
                            expected,
                            Arrays.asList("Execution terminates with error", "Undefined behavior", "No output produced"),
                            "Directly derived from the code / instructions documented in the uploaded file.",
                            qType,
                            cleanTopic
                    ));
                } else if ("DEBUGGING".equals(qType)) {
                    specs.add(new QuestionSpec(
                            "Identify the required syntax or logic condition for this document routine:",
                            snippet,
                            "Ensuring all referenced variables and constraints match the document specifications",
                            Arrays.asList(
                                    "Bypassing runtime validation checks completely",
                                    "Omitting return statements in functional routines",
                                    "Using undefined variables outside document scope"
                            ),
                            "Consistent with the specifications described in the uploaded document.",
                            qType,
                            cleanTopic
                    ));
                } else {
                    specs.add(new QuestionSpec(
                            "Which code logic accurately reflects the implementation described in the document?",
                            snippet,
                            "The snippet correctly implements the logic detailed in " + cleanTopic,
                            Arrays.asList(
                                    "An anti-pattern violating document constraints",
                                    "Deprecated syntax unsupported by the documented specification",
                                    "An unreachable block that triggers runtime termination"
                            ),
                            "Verified against the code patterns in the source material.",
                            qType,
                            cleanTopic
                    ));
                }
            } else {
                // Default MCQ / CONCEPTUAL / SENTENCE_CORRECTION based strictly on document
                if (!definitions.isEmpty()) {
                    String[] def = definitions.get(cycle % definitions.size());
                    String term = def[0];
                    String verb = def[1];
                    String desc = def[2];
                    String origSentence = def[3];

                    List<String> distractors = new ArrayList<>();
                    for (int d = 1; d < definitions.size() && distractors.size() < 3; d++) {
                        String otherDesc = definitions.get((cycle + d) % definitions.size())[2];
                        if (!otherDesc.equals(desc)) {
                            distractors.add(otherDesc);
                        }
                    }
                    while (distractors.size() < 3) {
                        distractors.add("Contradicts the core rules specified for " + cleanTopic);
                    }

                    String qText = "According to the document, what " + verb + " " + term + "?";
                    if ("CONCEPTUAL".equals(qType)) {
                        qText = "Based on the uploaded document, why is " + term + " significant in " + cleanTopic + "?";
                    } else if ("SENTENCE_CORRECTION".equals(qType)) {
                        qText = "Which statement regarding " + term + " is accurately stated in the document?";
                    }

                    specs.add(new QuestionSpec(
                            qText,
                            null,
                            desc,
                            distractors,
                            "Directly stated in the document: \"" + origSentence + "\"",
                            qType,
                            cleanTopic
                    ));
                } else if (!sentences.isEmpty()) {
                    String s = sentences.get(cycle % sentences.size());
                    String alt1 = sentences.get((cycle + 1) % sentences.size());

                    specs.add(new QuestionSpec(
                            "Which statement is directly confirmed by the uploaded document?",
                            null,
                            s,
                            Arrays.asList(
                                    "The opposite claim: " + alt1.replaceFirst("^([A-Z])", "Not $1"),
                                    "An unsubstantiated claim regarding " + cleanTopic,
                                    "A generic assertion not present in the source text"
                            ),
                            "Verbatim source text from document: \"" + s + "\"",
                            qType,
                            cleanTopic
                    ));
                }
            }

            cycle++;
        }

        // Build QuizQuestion objects from matchingSpecs
        List<QuizQuestion> questions = new ArrayList<>();
        Random random = new Random(quiz.getId().hashCode());
        int totalToTake = Math.min(count, specs.size());

        for (int i = 0; i < totalToTake; i++) {
            QuestionSpec spec = specs.get(i);
            List<String> options = new ArrayList<>();

            if ("TRUE_FALSE".equals(spec.type)) {
                options = Arrays.asList("True", "False");
                int correctIndex = "True".equalsIgnoreCase(spec.correctOption) ? 0 : 1;

                QuizQuestion qq = QuizQuestion.builder()
                        .quiz(quiz)
                        .question(spec.question)
                        .codeSnippet(spec.codeSnippet)
                        .options(options)
                        .correctAnswer(correctIndex)
                        .explanation(spec.explanation)
                        .type(spec.type)
                        .topic(spec.topic != null ? spec.topic : cleanTopic)
                        .difficulty(difficulty != null ? difficulty.name() : "MEDIUM")
                        .build();

                questions.add(quizQuestionRepository.save(qq));
            } else {
                options.add(spec.correctOption);
                options.addAll(spec.distractors);
                Collections.shuffle(options, random);
                int correctIndex = options.indexOf(spec.correctOption);

                QuizQuestion qq = QuizQuestion.builder()
                        .quiz(quiz)
                        .question(spec.question)
                        .codeSnippet(spec.codeSnippet)
                        .options(options)
                        .correctAnswer(correctIndex)
                        .explanation(spec.explanation)
                        .type(spec.type)
                        .topic(spec.topic != null ? spec.topic : cleanTopic)
                        .difficulty(difficulty != null ? difficulty.name() : "MEDIUM")
                        .build();

                questions.add(quizQuestionRepository.save(qq));
            }
        }

        return questions;
    }

    private Set<Long> getSavedQuestionIds(User user) {
        return savedQuestionRepository.findByUserOrderByCreatedAtDesc(user).stream()
                .map(sq -> sq.getQuestion().getId())
                .collect(Collectors.toSet());
    }

    private QuizDto toDto(Quiz quiz, User user) {
        Set<Long> savedIds = getSavedQuestionIds(user);
        List<QuizQuestionDto> qDtos = quizQuestionRepository.findByQuizOrderByIdAsc(quiz).stream()
                .map(q -> toQuestionDto(q, savedIds))
                .collect(Collectors.toList());

        boolean hasPdf = quizPdfRepository.existsByQuizId(quiz.getId());
        String dlUrl = "/api/v1/quiz/" + quiz.getId() + "/pdf/download";
        String prevUrl = "/api/v1/quiz/" + quiz.getId() + "/pdf/preview";
        String pdfFileName = quizPdfService.getPdfFileName(quiz.getId());

        return QuizDto.builder()
                .id(quiz.getId())
                .title(quiz.getTitle())
                .documentId(quiz.getDocument() != null ? quiz.getDocument().getId() : null)
                .difficulty(quiz.getDifficulty())
                .totalQuestions(quiz.getTotalQuestions())
                .completed(quiz.isCompleted())
                .score(quiz.getScore())
                .createdAt(quiz.getCreatedAt())
                .questions(qDtos)
                .pdfAvailable(hasPdf)
                .pdfFileName(pdfFileName)
                .pdfDownloadUrl(dlUrl)
                .pdfPreviewUrl(prevUrl)
                .pdfQuestionsCount(qDtos.size())
                .build();
    }

    private QuizQuestionDto toQuestionDto(QuizQuestion q, Set<Long> savedIds) {
        boolean answered = q.getUserAnswer() != null;
        Boolean isCorrect = (answered && q.getCorrectAnswer() != null)
                ? q.getUserAnswer().equals(q.getCorrectAnswer())
                : null;

        String selectedAnswerText = null;
        if (answered && q.getUserAnswer() >= 0 && q.getUserAnswer() < q.getOptions().size()) {
            selectedAnswerText = q.getOptions().get(q.getUserAnswer());
        }

        String correctAnswerText = null;
        if (q.getQuiz() != null && q.getQuiz().isCompleted() && q.getCorrectAnswer() != null && q.getCorrectAnswer() >= 0 && q.getCorrectAnswer() < q.getOptions().size()) {
            correctAnswerText = q.getOptions().get(q.getCorrectAnswer());
        }

        boolean saved = savedIds.contains(q.getId());
        String diffStr = q.getDifficulty() != null ? q.getDifficulty() : (q.getQuiz() != null && q.getQuiz().getDifficulty() != null ? q.getQuiz().getDifficulty().name() : "MEDIUM");
        String topicStr = q.getTopic() != null ? q.getTopic() : (q.getQuiz() != null && q.getQuiz().getDocument() != null ? q.getQuiz().getDocument().getFilename() : "General");

        return QuizQuestionDto.builder()
                .id(q.getId())
                .question(q.getQuestion())
                .questionText(q.getQuestion())
                .codeSnippet(q.getCodeSnippet())
                .sourceDocumentName(topicStr)
                .options(q.getOptions())
                .correctAnswer(correctAnswerText)
                .correctAnswerIndex(q.getQuiz() != null && q.getQuiz().isCompleted() ? q.getCorrectAnswer() : null)
                .explanation(q.getQuiz() != null && q.getQuiz().isCompleted() ? q.getExplanation() : null)
                .userAnswer(q.getUserAnswer())
                .selectedAnswer(selectedAnswerText)
                .isCorrect(isCorrect)
                .correct(isCorrect)
                .isSaved(saved)
                .saved(saved)
                .difficulty(diffStr)
                .topic(topicStr)
                .type(q.getType() != null ? q.getType() : "MCQ")
                .answered(answered)
                .build();
    }

    @Override
    @Transactional
    public void deleteQuiz(String quizId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        Quiz quiz = isAdmin
                ? quizRepository.findById(quizId).orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId))
                : quizRepository.findByIdAndUser(quizId, user).orElseThrow(() -> new ResourceNotFoundException("Quiz", "id", quizId));

        if (jdbcTemplate != null) {
            jdbcTemplate.update("DELETE FROM quiz_pdfs WHERE quiz_id = ?", quizId);
            jdbcTemplate.update(
                    "DELETE FROM quiz_question_options WHERE question_id IN (SELECT id FROM quiz_questions WHERE quiz_id = ?)",
                    quizId
            );
            jdbcTemplate.update("DELETE FROM quiz_questions WHERE quiz_id = ?", quizId);
        }
        quizRepository.delete(quiz);
        quizRepository.flush();
        log.info("Quiz {} successfully deleted from database for user {}", quizId, user.getId());
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    private boolean isVectorStoreTablePresent() {
        if (jdbcTemplate == null) return false;
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store')",
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            return false;
        }
    }
}
