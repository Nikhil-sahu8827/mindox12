package com.substring.docmind.quiz.service;

import com.substring.docmind.quiz.dto.*;
import com.substring.docmind.security.UserPrincipal;

import java.util.List;
import java.util.Map;

public interface QuizService {

    QuizDto generateQuiz(GenerateQuizRequest request, UserPrincipal currentUser);

    QuizDto getQuizById(String quizId, UserPrincipal currentUser);

    AnswerResultDto submitAnswer(String quizId, SubmitAnswerRequest request, UserPrincipal currentUser);

    QuizResultDto completeQuiz(String quizId, UserPrincipal currentUser);

    QuizResultDto getQuizResult(String quizId, UserPrincipal currentUser);

    List<QuizHistoryDto> getQuizHistory(UserPrincipal currentUser);

    QuizProgressDto getQuizProgress(UserPrincipal currentUser);

    List<QuizQuestionDto> getMistakes(UserPrincipal currentUser);

    void saveQuestion(Long questionId, UserPrincipal currentUser);

    void unsaveQuestion(Long questionId, UserPrincipal currentUser);

    List<QuizQuestionDto> getSavedQuestions(UserPrincipal currentUser);

    QuizQuestionDto getSimilarQuestion(Long questionId, UserPrincipal currentUser);

    DoubtResponseDto askQuestionDoubt(Long questionId, AskDoubtRequest request, UserPrincipal currentUser);

    Map<String, Object> analyzeWeaknesses(UserPrincipal currentUser);

    Map<String, Object> analyzeWeaknesses(List<String> documentIds, UserPrincipal currentUser);

    QuizDto appendBatchQuestions(String quizId, int count, com.substring.docmind.enums.DifficultyLevel difficulty, UserPrincipal currentUser);

    void deleteQuiz(String quizId, UserPrincipal currentUser);
}
