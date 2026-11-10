package com.substring.docmind.growth.service;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.document.repository.DocumentMetadataRepository;
import com.substring.docmind.growth.dto.*;
import com.substring.docmind.growth.entity.QuestionAttempt;
import com.substring.docmind.growth.entity.StudyTopic;
import com.substring.docmind.growth.entity.TopicProgressSnapshot;
import com.substring.docmind.growth.repository.QuestionAttemptRepository;
import com.substring.docmind.growth.repository.StudyTopicRepository;
import com.substring.docmind.growth.repository.TopicProgressSnapshotRepository;
import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizMistake;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.quiz.repository.QuizMistakeRepository;
import com.substring.docmind.quiz.repository.QuizQuestionRepository;
import com.substring.docmind.quiz.repository.QuizRepository;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class TopicGrowthServiceImpl implements TopicGrowthService {

    private final StudyTopicRepository studyTopicRepository;
    private final QuestionAttemptRepository questionAttemptRepository;
    private final TopicProgressSnapshotRepository snapshotRepository;
    private final DocumentMetadataRepository documentMetadataRepository;
    private final QuizRepository quizRepository;
    private final QuizQuestionRepository quizQuestionRepository;
    private final QuizMistakeRepository quizMistakeRepository;
    private final UserRepository userRepository;
    private final TopicExtractionService topicExtractionService;

    private static final int MIN_ATTEMPTS_FOR_CONFIDENCE = 3;

    @Override
    @Transactional
    public void recordQuestionAttempt(Long userId, QuizQuestion question, Integer selectedOption, boolean isCorrect, String documentId, String sourceFileName) {
        if (userId == null || question == null) return;

        User user = userRepository.findById(userId).orElse(null);
        if (user == null) return;

        String topicName = question.getTopic() != null && !question.getTopic().isBlank()
                ? question.getTopic().trim()
                : "General Practice";

        QuestionAttempt attempt = QuestionAttempt.builder()
                .user(user)
                .question(question)
                .quiz(question.getQuiz())
                .topicName(topicName)
                .documentId(documentId != null ? documentId : (question.getQuiz() != null && question.getQuiz().getDocument() != null ? question.getQuiz().getDocument().getId() : null))
                .sourceFileName(sourceFileName != null ? sourceFileName : (question.getQuiz() != null && question.getQuiz().getDocument() != null ? question.getQuiz().getDocument().getFilename() : "Study Resource"))
                .selectedOption(selectedOption)
                .isCorrect(isCorrect)
                .build();

        questionAttemptRepository.save(attempt);

        // Update / create today's topic snapshot for historical trend tracking
        updateDailySnapshot(user, topicName, attempt.getDocumentId(), attempt.getSourceFileName());
    }

    @Override
    @Transactional
    public TopicGrowthDashboardDto getGrowthDashboard(String timeRange, String documentId, UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        ensureUserTopicsInitialized(user);

        // Ensure legacy quiz answers are synced into attempts if any exist
        syncLegacyAttemptsIfEmpty(user);

        List<QuestionAttempt> allAttempts = questionAttemptRepository.findByUserOrderByCreatedAtDesc(user);
        if (documentId != null && !documentId.isBlank()) {
            allAttempts = allAttempts.stream()
                    .filter(a -> documentId.equalsIgnoreCase(a.getDocumentId()))
                    .collect(Collectors.toList());
        }

        // Apply time range filter if provided ("7d", "30d", "all")
        if ("7d".equalsIgnoreCase(timeRange)) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(7);
            allAttempts = allAttempts.stream().filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(cutoff)).collect(Collectors.toList());
        } else if ("30d".equalsIgnoreCase(timeRange)) {
            LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
            allAttempts = allAttempts.stream().filter(a -> a.getCreatedAt() != null && a.getCreatedAt().isAfter(cutoff)).collect(Collectors.toList());
        }

        List<StudyTopic> studyTopics = studyTopicRepository.findAllForUser(user.getId());
        if (documentId != null && !documentId.isBlank()) {
            studyTopics = studyTopics.stream()
                    .filter(t -> t.getDocument() != null && documentId.equalsIgnoreCase(t.getDocument().getId()))
                    .collect(Collectors.toList());
        }

        List<DocumentMetadata> documents = documentMetadataRepository.findAll();
        if (documentId != null && !documentId.isBlank()) {
            documents = documents.stream().filter(d -> documentId.equalsIgnoreCase(d.getId())).collect(Collectors.toList());
        }

        // Map attempts by topic name (case-insensitive)
        Map<String, List<QuestionAttempt>> attemptsByTopic = new HashMap<>();
        for (QuestionAttempt a : allAttempts) {
            String key = a.getTopicName().trim().toLowerCase();
            attemptsByTopic.computeIfAbsent(key, k -> new ArrayList<>()).add(a);
        }

        // User mistakes for topic strength calculation
        List<QuizMistake> userMistakes = quizMistakeRepository.findByUserOrderByCreatedAtDesc(user);

        // Group study topics into main topics and subtopics
        Map<Long, List<StudyTopic>> subtopicsByParent = studyTopics.stream()
                .filter(t -> t.getHierarchyLevel() == 2 && t.getParentTopicId() != null)
                .collect(Collectors.groupingBy(StudyTopic::getParentTopicId));

        List<StudyTopic> mainTopics = studyTopics.stream()
                .filter(t -> t.getHierarchyLevel() == 1)
                .collect(Collectors.toList());

        List<TopicHierarchyDto> hierarchyDtos = new ArrayList<>();
        int beginner = 0, developing = 0, intermediate = 0, strong = 0, excellent = 0, notEnough = 0;
        int totalAvailableQuestionsSum = 0;
        Set<Long> overallUniqueQuestionsAttempted = new HashSet<>();
        int overallCorrectCount = 0;
        int overallAttemptCount = allAttempts.size();

        for (QuestionAttempt a : allAttempts) {
            if (a.getQuestion() != null) {
                overallUniqueQuestionsAttempted.add(a.getQuestion().getId());
            }
            if (a.isCorrect()) {
                overallCorrectCount++;
            }
        }

        // Fetch snapshots for previous comparison
        Map<String, TopicProgressSnapshot> latestSnapshotsByTopic = new HashMap<>();
        Map<String, TopicProgressSnapshot> previousSnapshotsByTopic = new HashMap<>();
        List<TopicProgressSnapshot> allSnapshots = snapshotRepository.findByUserOrderBySnapshotDateAsc(user);
        for (TopicProgressSnapshot s : allSnapshots) {
            String key = s.getTopicName().trim().toLowerCase();
            if (latestSnapshotsByTopic.containsKey(key)) {
                previousSnapshotsByTopic.put(key, latestSnapshotsByTopic.get(key));
            }
            latestSnapshotsByTopic.put(key, s);
        }

        for (StudyTopic mt : mainTopics) {
            String normKey = mt.getTopicName().trim().toLowerCase();
            String docId = mt.getDocument() != null ? mt.getDocument().getId() : null;
            String strippedKey = mt.getNormalizedKey() != null ? mt.getNormalizedKey() : normKey.replaceAll("[^a-z0-9]", "");

            List<QuestionAttempt> topicAttempts = allAttempts.stream()
                    .filter(a -> {
                        if (docId != null && docId.equalsIgnoreCase(a.getDocumentId())) return true;
                        String aKey = a.getTopicName().trim().toLowerCase();
                        if (aKey.equals(normKey)) return true;
                        String aStripped = aKey.replaceAll("[^a-z0-9]", "");
                        if (aStripped.equals(strippedKey)) return true;
                        if (!aStripped.isBlank() && !strippedKey.isBlank() && (aStripped.contains(strippedKey) || strippedKey.contains(aStripped))) return true;
                        return false;
                    })
                    .collect(Collectors.toList());

            // Build subtopics
            List<StudyTopic> subList = subtopicsByParent.getOrDefault(mt.getId(), Collections.emptyList());
            List<SubtopicProgressDto> subDtos = new ArrayList<>();

            int topicAvailQuestions = mt.getEstimatedTotalQuestions() != null ? mt.getEstimatedTotalQuestions() : 20;
            totalAvailableQuestionsSum += topicAvailQuestions;

            // Unique questions attempted for this topic
            Set<Long> uniqueQIds = new HashSet<>();
            int correctInTopic = 0;
            for (QuestionAttempt qa : topicAttempts) {
                if (qa.getQuestion() != null) uniqueQIds.add(qa.getQuestion().getId());
                if (qa.isCorrect()) correctInTopic++;
            }

            int uniqueAttempted = uniqueQIds.size();
            int totalAttempts = topicAttempts.size();
            int incorrect = totalAttempts - correctInTopic;
            int unattempted = Math.max(0, topicAvailQuestions - uniqueAttempted);

            double completionPct = topicAvailQuestions > 0
                    ? Math.round(((double) uniqueAttempted / topicAvailQuestions) * 1000.0) / 10.0
                    : 0.0;
            completionPct = Math.min(100.0, completionPct);

            double accuracyPct = totalAttempts > 0
                    ? Math.round(((double) correctInTopic / totalAttempts) * 1000.0) / 10.0
                    : 0.0;

            // Strength calculation
            long topicMistakes = userMistakes.stream()
                    .filter(m -> {
                        if (m.getQuestion() == null) return false;
                        if (docId != null && m.getQuestion().getQuiz() != null && m.getQuestion().getQuiz().getDocument() != null && docId.equalsIgnoreCase(m.getQuestion().getQuiz().getDocument().getId())) return true;
                        if (m.getQuestion().getTopic() != null) {
                            String mStripped = m.getQuestion().getTopic().trim().toLowerCase().replaceAll("[^a-z0-9]", "");
                            return !mStripped.isBlank() && (mStripped.contains(strippedKey) || strippedKey.contains(mStripped));
                        }
                        return false;
                    })
                    .count();
            double strengthScore = calculateStrengthScore(topicAttempts, completionPct, accuracyPct, topicMistakes);
            String strengthLevel = getStrengthLevel(totalAttempts, strengthScore);
            String confidence = totalAttempts >= MIN_ATTEMPTS_FOR_CONFIDENCE ? "High" : (totalAttempts > 0 ? "Moderate" : "Insufficient");

            // Count strength categories
            if (totalAttempts < MIN_ATTEMPTS_FOR_CONFIDENCE) notEnough++;
            else if (strengthScore >= 90) excellent++;
            else if (strengthScore >= 75) strong++;
            else if (strengthScore >= 60) intermediate++;
            else if (strengthScore >= 40) developing++;
            else beginner++;

            // Subtopics calculation
            for (StudyTopic st : subList) {
                String subKey = (st.getSubtopicName() != null ? st.getSubtopicName() : st.getTopicName()).trim().toLowerCase();
                // Filter attempts matching subtopic name keywords
                List<QuestionAttempt> subAttempts = topicAttempts.stream()
                        .filter(a -> a.getQuestion() != null && a.getQuestion().getQuestion() != null &&
                                (a.getQuestion().getQuestion().toLowerCase().contains(subKey) ||
                                 (a.getQuestion().getExplanation() != null && a.getQuestion().getExplanation().toLowerCase().contains(subKey))))
                        .collect(Collectors.toList());

                int subAvail = st.getEstimatedTotalQuestions() != null ? st.getEstimatedTotalQuestions() : 8;
                Set<Long> subUniqueQIds = subAttempts.stream().map(a -> a.getQuestion().getId()).collect(Collectors.toSet());
                int subCorrect = (int) subAttempts.stream().filter(QuestionAttempt::isCorrect).count();
                int subTotal = subAttempts.size();
                int subUnattempted = Math.max(0, subAvail - subUniqueQIds.size());
                double subComp = subAvail > 0 ? Math.min(100.0, Math.round(((double) subUniqueQIds.size() / subAvail) * 1000.0) / 10.0) : 0.0;
                double subAcc = subTotal > 0 ? Math.round(((double) subCorrect / subTotal) * 1000.0) / 10.0 : 0.0;
                double subStr = calculateStrengthScore(subAttempts, subComp, subAcc, 0);

                subDtos.add(SubtopicProgressDto.builder()
                        .id(st.getId())
                        .name(st.getSubtopicName() != null ? st.getSubtopicName() : st.getTopicName())
                        .topicName(mt.getTopicName())
                        .sourceFileName(st.getSourceFileName())
                        .documentId(st.getDocument() != null ? st.getDocument().getId() : null)
                        .totalAvailableQuestions(subAvail)
                        .uniqueAttemptedQuestions(subUniqueQIds.size())
                        .totalAttempts(subTotal)
                        .correctAnswers(subCorrect)
                        .incorrectAnswers(subTotal - subCorrect)
                        .unattemptedQuestions(subUnattempted)
                        .completionPercentage(subComp)
                        .accuracyPercentage(subAcc)
                        .strengthScore(subStr)
                        .strengthLevel(getStrengthLevel(subTotal, subStr))
                        .confidence(subTotal >= MIN_ATTEMPTS_FOR_CONFIDENCE ? "High" : (subTotal > 0 ? "Moderate" : "Insufficient"))
                        .lastPracticedDate(subAttempts.isEmpty() ? null : subAttempts.get(0).getCreatedAt())
                        .build());
            }

            // Historical Comparison
            TopicProgressSnapshot prev = previousSnapshotsByTopic.get(normKey);
            Double prevScore = prev != null ? prev.getStrengthScore() : null;
            Double change = prevScore != null ? Math.round((strengthScore - prevScore) * 10.0) / 10.0 : 0.0;
            Boolean isImproving = prevScore != null ? (strengthScore >= prevScore) : null;

            LocalDateTime lastPracticed = topicAttempts.isEmpty() ? null : topicAttempts.get(0).getCreatedAt();

            // Total practice sessions = count of distinct quizzes for this topic
            long totalSessions = topicAttempts.stream()
                    .map(a -> a.getQuiz() != null ? a.getQuiz().getId() : a.getCreatedAt().toLocalDate().toString())
                    .distinct().count();

            TopicHierarchyDto hDto = TopicHierarchyDto.builder()
                    .id(mt.getId())
                    .topicName(mt.getTopicName())
                    .normalizedKey(mt.getNormalizedKey())
                    .sourceFileName(mt.getSourceFileName())
                    .documentId(mt.getDocument() != null ? mt.getDocument().getId() : null)
                    .description(mt.getDescription())
                    .hierarchyLevel(1)
                    .subtopics(subDtos)
                    .totalAvailableQuestions(topicAvailQuestions)
                    .uniqueAttemptedQuestions(uniqueAttempted)
                    .totalAttempts(totalAttempts)
                    .correctAnswers(correctInTopic)
                    .incorrectAnswers(incorrect)
                    .unattemptedQuestions(unattempted)
                    .completionPercentage(completionPct)
                    .accuracyPercentage(accuracyPct)
                    .strengthScore(strengthScore)
                    .strengthLevel(strengthLevel)
                    .confidence(confidence)
                    .lastPracticedDate(lastPracticed)
                    .totalPracticeSessions((int) totalSessions)
                    .previousStrengthScore(prevScore)
                    .strengthChange(change)
                    .isImproving(isImproving)
                    .build();

            hierarchyDtos.add(hDto);
        }

        // Categorize topics for quick dashboard views
        List<TopicHierarchyDto> strongTopics = hierarchyDtos.stream()
                .filter(t -> t.getStrengthScore() >= 75 && t.getTotalAttempts() >= MIN_ATTEMPTS_FOR_CONFIDENCE)
                .sorted(Comparator.comparingDouble(TopicHierarchyDto::getStrengthScore).reversed())
                .collect(Collectors.toList());

        List<TopicHierarchyDto> weakTopics = hierarchyDtos.stream()
                .filter(t -> t.getTotalAttempts() >= MIN_ATTEMPTS_FOR_CONFIDENCE && t.getStrengthScore() < 60)
                .sorted(Comparator.comparingDouble(TopicHierarchyDto::getStrengthScore))
                .collect(Collectors.toList());

        List<TopicHierarchyDto> needsPracticeTopics = hierarchyDtos.stream()
                .filter(t -> t.getCompletionPercentage() < 50 || t.getStrengthScore() < 65)
                .sorted(Comparator.comparingDouble(TopicHierarchyDto::getCompletionPercentage))
                .collect(Collectors.toList());

        List<TopicHierarchyDto> mostImprovedTopics = hierarchyDtos.stream()
                .filter(t -> t.getStrengthChange() != null && t.getStrengthChange() > 0)
                .sorted(Comparator.comparing(TopicHierarchyDto::getStrengthChange).reversed())
                .collect(Collectors.toList());

        // File-wise progress breakdown
        List<FileProgressDto> fileProgressList = buildFileProgressList(documents, hierarchyDtos, allAttempts);

        // Daily activity tracking (last 14 days)
        List<ActivitySnapshotDto> dailyActivity = buildDailyActivity(allAttempts);

        // Weekly activity tracking (last 4 weeks)
        List<ActivitySnapshotDto> weeklyActivity = buildWeeklyActivity(allAttempts);

        // Historical Strength Over Time Series for Charts
        List<Map<String, Object>> strengthOverTime = buildStrengthOverTime(allSnapshots, hierarchyDtos);

        // Accuracy Comparison Bar Chart data
        List<Map<String, Object>> topicAccuracyComparison = hierarchyDtos.stream()
                .map(t -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("topic", t.getTopicName());
                    map.put("accuracy", t.getAccuracyPercentage());
                    map.put("attempts", t.getTotalAttempts());
                    map.put("strength", t.getStrengthScore());
                    return map;
                })
                .collect(Collectors.toList());

        // Overall summary statistics
        int totalUniqueAttempted = overallUniqueQuestionsAttempted.size();
        double overallCompletion = totalAvailableQuestionsSum > 0
                ? Math.min(100.0, Math.round(((double) totalUniqueAttempted / totalAvailableQuestionsSum) * 1000.0) / 10.0)
                : 0.0;

        double overallAccuracy = overallAttemptCount > 0
                ? Math.round(((double) overallCorrectCount / overallAttemptCount) * 1000.0) / 10.0
                : 0.0;

        double avgStrength = hierarchyDtos.stream()
                .filter(t -> t.getTotalAttempts() >= MIN_ATTEMPTS_FOR_CONFIDENCE)
                .mapToDouble(TopicHierarchyDto::getStrengthScore)
                .average()
                .orElse(0.0);
        avgStrength = Math.round(avgStrength * 10.0) / 10.0;

        int unattemptedQuestionsSum = Math.max(0, totalAvailableQuestionsSum - totalUniqueAttempted);

        // Calculate a personalized daily practice target
        int dailyTarget = Math.max(5, Math.min(25, (int) Math.ceil((double) unattemptedQuestionsSum / 7.0)));

        // Personalized smart recommendations
        List<PersonalizedRecommendationDto> recommendations = buildRecommendations(
                weakTopics, strongTopics, needsPracticeTopics, hierarchyDtos, unattemptedQuestionsSum, dailyTarget
        );

        return TopicGrowthDashboardDto.builder()
                .overallCompletionPercentage(overallCompletion)
                .overallAccuracy(overallAccuracy)
                .overallAverageStrength(avgStrength)
                .totalAvailableQuestions(totalAvailableQuestionsSum)
                .totalUniqueQuestionsAttempted(totalUniqueAttempted)
                .totalPracticeSessions((int) allAttempts.stream().map(a -> a.getCreatedAt().toLocalDate()).distinct().count())
                .totalAttemptsCount(overallAttemptCount)
                .totalCorrectAnswers(overallCorrectCount)
                .totalIncorrectAnswers(overallAttemptCount - overallCorrectCount)
                .unattemptedQuestions(unattemptedQuestionsSum)
                .beginnerCount(beginner)
                .developingCount(developing)
                .intermediateCount(intermediate)
                .strongCount(strong)
                .excellentCount(excellent)
                .notEnoughDataCount(notEnough)
                .totalTopicsCount(hierarchyDtos.size())
                .totalFilesCount(fileProgressList.size())
                .dailyPracticeTarget(dailyTarget)
                .strongTopics(strongTopics)
                .weakTopics(weakTopics)
                .needsPracticeTopics(needsPracticeTopics)
                .mostImprovedTopics(mostImprovedTopics)
                .allTopics(hierarchyDtos)
                .filesProgress(fileProgressList)
                .dailyActivity(dailyActivity)
                .weeklyActivity(weeklyActivity)
                .strengthOverTime(strengthOverTime)
                .topicAccuracyComparison(topicAccuracyComparison)
                .recommendations(recommendations)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TopicHierarchyDto> getTopicHierarchy(String documentId, UserPrincipal currentUser) {
        TopicGrowthDashboardDto dashboard = getGrowthDashboard("all", documentId, currentUser);
        return dashboard.getAllTopics();
    }

    @Override
    @Transactional(readOnly = true)
    public Map<String, Object> getTopicPracticeHistory(Long topicId, UserPrincipal currentUser) {
        User user = userRepository.findById(currentUser.getId()).orElseThrow();
        StudyTopic topic = studyTopicRepository.findById(topicId)
                .orElseThrow(() -> new IllegalArgumentException("StudyTopic not found: " + topicId));

        List<QuestionAttempt> attempts = questionAttemptRepository.findByUserAndTopicNameOrderByCreatedAtDesc(user, topic.getTopicName());
        List<TopicProgressSnapshot> snapshots = snapshotRepository.findByUserAndTopicNameOrderBySnapshotDateAsc(user, topic.getTopicName());

        Map<String, Object> result = new HashMap<>();
        result.put("topic", topic.getTopicName());
        result.put("sourceFileName", topic.getSourceFileName());
        result.put("totalAttempts", attempts.size());
        result.put("correctCount", attempts.stream().filter(QuestionAttempt::isCorrect).count());
        result.put("snapshots", snapshots);
        result.put("attempts", attempts.stream().limit(50).map(a -> {
            Map<String, Object> m = new HashMap<>();
            m.put("id", a.getId());
            m.put("question", a.getQuestion() != null ? a.getQuestion().getQuestion() : "");
            m.put("isCorrect", a.isCorrect());
            m.put("selectedOption", a.getSelectedOption());
            m.put("date", a.getCreatedAt());
            return m;
        }).collect(Collectors.toList()));

        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public List<PersonalizedRecommendationDto> getRecommendations(UserPrincipal currentUser) {
        TopicGrowthDashboardDto dashboard = getGrowthDashboard("all", null, currentUser);
        return dashboard.getRecommendations();
    }

    // --- Private Helper Calculation & Construction Methods ---

    private double calculateStrengthScore(List<QuestionAttempt> attempts, double completionPct, double accuracyPct, long mistakeCount) {
        if (attempts.isEmpty()) return 0.0;

        // 1. Accuracy Component (50%)
        double accScore = accuracyPct * 0.50;

        // 2. Recent Performance Component (30% based on last 5 attempts)
        int recentCount = Math.min(5, attempts.size());
        int recentCorrect = 0;
        for (int i = 0; i < recentCount; i++) {
            if (attempts.get(i).isCorrect()) recentCorrect++;
        }
        double recentAcc = recentCount > 0 ? ((double) recentCorrect / recentCount) * 100.0 : accuracyPct;
        double recentScore = recentAcc * 0.30;

        // 3. Coverage Component (20%)
        double coverageScore = Math.min(100.0, completionPct) * 0.20;

        // 4. Repeated Mistake Penalty (Deduction up to 15 points)
        double mistakePenalty = Math.min(15.0, mistakeCount * 3.0);

        double total = (accScore + recentScore + coverageScore) - mistakePenalty;
        total = Math.max(0.0, Math.min(100.0, total));
        return Math.round(total * 10.0) / 10.0;
    }

    private String getStrengthLevel(int attempts, double score) {
        if (attempts < MIN_ATTEMPTS_FOR_CONFIDENCE) return "Not Enough Data";
        if (score >= 90) return "Excellent";
        if (score >= 75) return "Strong";
        if (score >= 60) return "Intermediate";
        if (score >= 40) return "Developing";
        return "Beginner";
    }

    private void updateDailySnapshot(User user, String topicName, String documentId, String sourceFileName) {
        try {
            LocalDate today = LocalDate.now();
            List<QuestionAttempt> topicAttempts = questionAttemptRepository.findByUserAndTopicNameOrderByCreatedAtDesc(user, topicName);
            if (topicAttempts.isEmpty()) return;

            int correct = (int) topicAttempts.stream().filter(QuestionAttempt::isCorrect).count();
            int total = topicAttempts.size();
            double acc = total > 0 ? Math.round(((double) correct / total) * 1000.0) / 10.0 : 0.0;
            double str = calculateStrengthScore(topicAttempts, 50.0, acc, 0);

            TopicProgressSnapshot snapshot = snapshotRepository.findByUserAndTopicNameAndSnapshotDate(user, topicName, today)
                    .orElseGet(() -> TopicProgressSnapshot.builder()
                            .user(user)
                            .topicName(topicName)
                            .documentId(documentId)
                            .sourceFileName(sourceFileName)
                            .snapshotDate(today)
                            .build()
                    );

            snapshot.setStrengthScore(str);
            snapshot.setAccuracy(acc);
            snapshot.setQuestionsAttempted(total);
            snapshot.setQuestionsCorrect(correct);
            snapshotRepository.save(snapshot);
        } catch (Exception e) {
            log.warn("Failed to update daily topic snapshot: {}", e.getMessage());
        }
    }

    private List<FileProgressDto> buildFileProgressList(List<DocumentMetadata> docs, List<TopicHierarchyDto> allTopics, List<QuestionAttempt> allAttempts) {
        List<FileProgressDto> list = new ArrayList<>();
        Map<String, List<TopicHierarchyDto>> topicsByDocId = allTopics.stream()
                .filter(t -> t.getDocumentId() != null)
                .collect(Collectors.groupingBy(TopicHierarchyDto::getDocumentId));

        Map<String, List<QuestionAttempt>> attemptsByDocId = allAttempts.stream()
                .filter(a -> a.getDocumentId() != null)
                .collect(Collectors.groupingBy(QuestionAttempt::getDocumentId));

        for (DocumentMetadata doc : docs) {
            String docId = doc.getId();
            List<TopicHierarchyDto> fileTopics = topicsByDocId.getOrDefault(docId, Collections.emptyList());
            List<QuestionAttempt> fileAttempts = attemptsByDocId.getOrDefault(docId, Collections.emptyList());

            int totalAvail = fileTopics.stream().mapToInt(TopicHierarchyDto::getTotalAvailableQuestions).sum();
            if (totalAvail == 0) totalAvail = 20;

            Set<Long> uniqueQ = fileAttempts.stream().filter(a -> a.getQuestion() != null).map(a -> a.getQuestion().getId()).collect(Collectors.toSet());
            int correct = (int) fileAttempts.stream().filter(QuestionAttempt::isCorrect).count();
            int totalAtt = fileAttempts.size();

            double comp = totalAvail > 0 ? Math.min(100.0, Math.round(((double) uniqueQ.size() / totalAvail) * 1000.0) / 10.0) : 0.0;
            double acc = totalAtt > 0 ? Math.round(((double) correct / totalAtt) * 1000.0) / 10.0 : 0.0;
            double strength = calculateStrengthScore(fileAttempts, comp, acc, 0);

            LocalDateTime lastPracticed = fileAttempts.isEmpty() ? null : fileAttempts.get(0).getCreatedAt();

            list.add(FileProgressDto.builder()
                    .documentId(docId)
                    .fileName(doc.getFilename())
                    .contentType(doc.getContentType())
                    .fileSize(doc.getFileSize())
                    .totalPages(doc.getTotalPages())
                    .totalTopics(fileTopics.size())
                    .totalAvailableQuestions(totalAvail)
                    .uniqueAttemptedQuestions(uniqueQ.size())
                    .totalAttempts(totalAtt)
                    .correctAnswers(correct)
                    .incorrectAnswers(totalAtt - correct)
                    .completionPercentage(comp)
                    .accuracyPercentage(acc)
                    .strengthScore(strength)
                    .strengthLevel(getStrengthLevel(totalAtt, strength))
                    .lastPracticedAt(lastPracticed)
                    .topics(fileTopics)
                    .build());
        }

        return list;
    }

    private List<ActivitySnapshotDto> buildDailyActivity(List<QuestionAttempt> attempts) {
        List<ActivitySnapshotDto> list = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
        LocalDate today = LocalDate.now();

        // 14 days back to today
        for (int i = 13; i >= 0; i--) {
            LocalDate day = today.minusDays(i);
            List<QuestionAttempt> dayAttempts = attempts.stream()
                    .filter(a -> a.getCreatedAt() != null && a.getCreatedAt().toLocalDate().isEqual(day))
                    .collect(Collectors.toList());

            int attCount = dayAttempts.size();
            int correct = (int) dayAttempts.stream().filter(QuestionAttempt::isCorrect).count();
            double acc = attCount > 0 ? Math.round(((double) correct / attCount) * 1000.0) / 10.0 : 0.0;

            list.add(ActivitySnapshotDto.builder()
                    .date(day)
                    .label(day.format(fmt))
                    .questionsAttempted(attCount)
                    .correctAnswers(correct)
                    .accuracyPercentage(acc)
                    .averageStrength(acc)
                    .build());
        }

        return list;
    }

    private List<ActivitySnapshotDto> buildWeeklyActivity(List<QuestionAttempt> attempts) {
        List<ActivitySnapshotDto> list = new ArrayList<>();
        LocalDate today = LocalDate.now();

        // 4 weeks
        for (int i = 3; i >= 0; i--) {
            LocalDate start = today.minusWeeks(i).minusDays(today.minusWeeks(i).getDayOfWeek().getValue() - 1);
            LocalDate end = start.plusDays(6);

            List<QuestionAttempt> weekAttempts = attempts.stream()
                    .filter(a -> a.getCreatedAt() != null &&
                            !a.getCreatedAt().toLocalDate().isBefore(start) &&
                            !a.getCreatedAt().toLocalDate().isAfter(end))
                    .collect(Collectors.toList());

            int attCount = weekAttempts.size();
            int correct = (int) weekAttempts.stream().filter(QuestionAttempt::isCorrect).count();
            double acc = attCount > 0 ? Math.round(((double) correct / attCount) * 1000.0) / 10.0 : 0.0;

            list.add(ActivitySnapshotDto.builder()
                    .date(start)
                    .label("Week of " + start.format(DateTimeFormatter.ofPattern("MMM dd")))
                    .questionsAttempted(attCount)
                    .correctAnswers(correct)
                    .accuracyPercentage(acc)
                    .averageStrength(acc)
                    .build());
        }

        return list;
    }

    private List<Map<String, Object>> buildStrengthOverTime(List<TopicProgressSnapshot> snapshots, List<TopicHierarchyDto> topics) {
        List<Map<String, Object>> list = new ArrayList<>();
        Map<LocalDate, List<TopicProgressSnapshot>> byDate = snapshots.stream()
                .collect(Collectors.groupingBy(TopicProgressSnapshot::getSnapshotDate));

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("MMM dd");
        List<LocalDate> sortedDates = byDate.keySet().stream().sorted().collect(Collectors.toList());

        // If no snapshots exist yet, build a synthetic initial data point from current topic strengths
        if (sortedDates.isEmpty()) {
            Map<String, Object> point = new HashMap<>();
            point.put("date", LocalDate.now().format(fmt));
            for (TopicHierarchyDto t : topics.stream().limit(5).collect(Collectors.toList())) {
                point.put(t.getTopicName(), t.getStrengthScore());
            }
            list.add(point);
            return list;
        }

        for (LocalDate d : sortedDates) {
            Map<String, Object> point = new HashMap<>();
            point.put("date", d.format(fmt));
            List<TopicProgressSnapshot> daySnaps = byDate.get(d);
            for (TopicProgressSnapshot s : daySnaps) {
                point.put(s.getTopicName(), s.getStrengthScore());
            }
            list.add(point);
        }

        return list;
    }

    private List<PersonalizedRecommendationDto> buildRecommendations(
            List<TopicHierarchyDto> weakTopics,
            List<TopicHierarchyDto> strongTopics,
            List<TopicHierarchyDto> needsPracticeTopics,
            List<TopicHierarchyDto> allTopics,
            int unattemptedCount,
            int dailyTarget
    ) {
        List<PersonalizedRecommendationDto> recs = new ArrayList<>();

        // 1. Weak Topics Recommendation (< 60% accuracy)
        for (TopicHierarchyDto weak : weakTopics.stream().limit(2).collect(Collectors.toList())) {
            recs.add(PersonalizedRecommendationDto.builder()
                    .id("rec-weak-" + weak.getId())
                    .type("REVISE_WEAK")
                    .priority("HIGH")
                    .title("Reinforce " + weak.getTopicName())
                    .reason("Your accuracy on this topic is currently " + weak.getAccuracyPercentage() + "%. Focused practice will eliminate knowledge gaps.")
                    .topicName(weak.getTopicName())
                    .documentId(weak.getDocumentId())
                    .sourceFileName(weak.getSourceFileName())
                    .actionLabel("Practice This Topic")
                    .currentStrength(weak.getStrengthScore())
                    .currentAccuracy(weak.getAccuracyPercentage())
                    .build());
        }

        // 2. High Unattempted / Needs Practice Recommendation
        for (TopicHierarchyDto need : needsPracticeTopics.stream().limit(2).collect(Collectors.toList())) {
            if (recs.stream().noneMatch(r -> r.getTopicName().equalsIgnoreCase(need.getTopicName()))) {
                recs.add(PersonalizedRecommendationDto.builder()
                        .id("rec-need-" + need.getId())
                        .type("PRACTICE_AGAIN")
                        .priority("MEDIUM")
                        .title("Expand Coverage: " + need.getTopicName())
                        .reason("You have completed " + need.getCompletionPercentage() + "% of available questions for this topic. " + need.getUnattemptedQuestions() + " questions remain unattempted.")
                        .topicName(need.getTopicName())
                        .documentId(need.getDocumentId())
                        .sourceFileName(need.getSourceFileName())
                        .actionLabel("Practice This Topic")
                        .currentStrength(need.getStrengthScore())
                        .currentAccuracy(need.getAccuracyPercentage())
                        .build());
            }
        }

        // 3. Revisit Previously Strong Topics that Declined
        for (TopicHierarchyDto topic : allTopics) {
            if (topic.getStrengthChange() != null && topic.getStrengthChange() < -5.0) {
                recs.add(PersonalizedRecommendationDto.builder()
                        .id("rec-decline-" + topic.getId())
                        .type("REVISIT_DECLINED")
                        .priority("HIGH")
                        .title("Retention Check: " + topic.getTopicName())
                        .reason("Your strength score dropped by " + Math.abs(topic.getStrengthChange()) + " points recently. Take a quick refresher quiz to restore your mastery.")
                        .topicName(topic.getTopicName())
                        .documentId(topic.getDocumentId())
                        .sourceFileName(topic.getSourceFileName())
                        .actionLabel("Practice This Topic")
                        .currentStrength(topic.getStrengthScore())
                        .currentAccuracy(topic.getAccuracyPercentage())
                        .build());
                break;
            }
        }

        // 4. Advance Next Recommendation for Strong Topics
        for (TopicHierarchyDto strong : strongTopics.stream().limit(1).collect(Collectors.toList())) {
            recs.add(PersonalizedRecommendationDto.builder()
                    .id("rec-strong-" + strong.getId())
                    .type("ADVANCE_NEXT")
                    .priority("LOW")
                    .title("Mastery Achieved in " + strong.getTopicName())
                    .reason("Outstanding performance! You have reached " + strong.getStrengthLevel() + " strength (" + strong.getStrengthScore() + "). Ready to tackle the next unit.")
                    .topicName(strong.getTopicName())
                    .documentId(strong.getDocumentId())
                    .sourceFileName(strong.getSourceFileName())
                    .actionLabel("Practice This Topic")
                    .currentStrength(strong.getStrengthScore())
                    .currentAccuracy(strong.getAccuracyPercentage())
                    .build());
        }

        // 5. Daily Practice Target Recommendation
        if (unattemptedCount > 0) {
            recs.add(PersonalizedRecommendationDto.builder()
                    .id("rec-daily-target")
                    .type("DAILY_TARGET")
                    .priority("MEDIUM")
                    .title("Daily Learning Target: " + dailyTarget + " Questions")
                    .reason("Based on your learning schedule and " + unattemptedCount + " remaining questions, practicing " + dailyTarget + " questions today will keep your growth on track.")
                    .topicName("Daily Goal")
                    .actionLabel("Start Daily Practice")
                    .build());
        }

        return recs;
    }

    private void ensureUserTopicsInitialized(User user) {
        long topicCount = studyTopicRepository.count();
        if (topicCount == 0) {
            log.info("No study topics found in database. Triggering automatic file & topic scan in background...");
            new Thread(() -> {
                try {
                    topicExtractionService.scanAndExtractAllTopics();
                } catch (Exception e) {
                    log.warn("Background topic scan encountered an issue: {}", e.getMessage());
                }
            }).start();
        }
    }

    private void syncLegacyAttemptsIfEmpty(User user) {
        long attemptCount = questionAttemptRepository.countByUser(user);
        if (attemptCount == 0) {
            log.info("No question attempts found for user {}. Backfilling from historical quiz answers...", user.getId());
            List<Quiz> quizzes = quizRepository.findByUserOrderByCreatedAtDesc(user);
            int synced = 0;
            for (Quiz quiz : quizzes) {
                List<QuizQuestion> questions = quizQuestionRepository.findByQuizOrderByIdAsc(quiz);
                for (QuizQuestion q : questions) {
                    if (q.getUserAnswer() != null) {
                        boolean isCorrect = q.getUserAnswer().equals(q.getCorrectAnswer());
                        String docId = quiz.getDocument() != null ? quiz.getDocument().getId() : null;
                        String fname = quiz.getDocument() != null ? quiz.getDocument().getFilename() : "Direct Quiz";
                        String topic = q.getTopic() != null && !q.getTopic().isBlank() ? q.getTopic().trim() : "General Practice";

                        QuestionAttempt attempt = QuestionAttempt.builder()
                                .user(user)
                                .question(q)
                                .quiz(quiz)
                                .topicName(topic)
                                .documentId(docId)
                                .sourceFileName(fname)
                                .selectedOption(q.getUserAnswer())
                                .isCorrect(isCorrect)
                                .createdAt(q.getQuiz().getCreatedAt() != null ? q.getQuiz().getCreatedAt() : LocalDateTime.now())
                                .build();

                        questionAttemptRepository.save(attempt);
                        synced++;
                    }
                }
            }
            if (synced > 0) {
                log.info("Successfully backfilled {} historical question attempts for user {}.", synced, user.getId());
            }
        }
    }
}
