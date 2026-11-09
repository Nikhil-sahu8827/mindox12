package com.substring.docmind.quiz;

import com.substring.docmind.enums.DifficultyLevel;
import com.substring.docmind.enums.Role;
import com.substring.docmind.quiz.dto.AnswerResultDto;
import com.substring.docmind.quiz.dto.QuizResultDto;
import com.substring.docmind.quiz.dto.SubmitAnswerRequest;
import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.quiz.repository.QuizMistakeRepository;
import com.substring.docmind.quiz.repository.QuizQuestionRepository;
import com.substring.docmind.quiz.repository.QuizRepository;
import com.substring.docmind.quiz.repository.SavedQuestionRepository;
import com.substring.docmind.quiz.service.QuizServiceImpl;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QuizServiceTest {

    @Mock
    private QuizRepository quizRepository;

    @Mock
    private QuizQuestionRepository quizQuestionRepository;

    @Mock
    private SavedQuestionRepository savedQuestionRepository;

    @Mock
    private QuizMistakeRepository quizMistakeRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private QuizServiceImpl quizService;

    private User sampleUser;
    private UserPrincipal samplePrincipal;
    private Quiz sampleQuiz;
    private QuizQuestion sampleQuestion;

    @BeforeEach
    void setUp() {
        sampleUser = User.builder()
                .id(1L)
                .username("testuser")
                .email("test@example.com")
                .role(Role.ROLE_USER)
                .build();

        samplePrincipal = UserPrincipal.create(sampleUser);

        sampleQuiz = Quiz.builder()
                .id("quiz_123")
                .user(sampleUser)
                .title("Test Quiz")
                .difficulty(DifficultyLevel.MEDIUM)
                .totalQuestions(1)
                .completed(false)
                .score(0)
                .build();

        sampleQuestion = QuizQuestion.builder()
                .id(10L)
                .quiz(sampleQuiz)
                .question("What is RAG?")
                .options(List.of("Retrieval Augmented Generation", "Random Access Gateway", "Remote Audio Guide", "None"))
                .correctAnswer(0)
                .explanation("RAG combines retrieval with LLM generation.")
                .build();
    }

    @Test
    void submitAnswer_CorrectAnswer_ReturnsCorrectTrue() {
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .questionId(10L)
                .selectedOption(0)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(quizRepository.findByIdAndUser("quiz_123", sampleUser)).thenReturn(Optional.of(sampleQuiz));
        when(quizQuestionRepository.findById(10L)).thenReturn(Optional.of(sampleQuestion));

        AnswerResultDto result = quizService.submitAnswer("quiz_123", request, samplePrincipal);

        assertNotNull(result);
        assertTrue(result.isCorrect());
        assertEquals(0, result.getCorrectAnswer());
        verify(quizMistakeRepository, times(1)).deleteByUserAndQuestion(sampleUser, sampleQuestion);
    }

    @Test
    void submitAnswer_WrongAnswer_TracksMistake() {
        SubmitAnswerRequest request = SubmitAnswerRequest.builder()
                .questionId(10L)
                .selectedOption(1)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(quizRepository.findByIdAndUser("quiz_123", sampleUser)).thenReturn(Optional.of(sampleQuiz));
        when(quizQuestionRepository.findById(10L)).thenReturn(Optional.of(sampleQuestion));
        when(quizMistakeRepository.findByUserAndQuestion(sampleUser, sampleQuestion)).thenReturn(Optional.empty());

        AnswerResultDto result = quizService.submitAnswer("quiz_123", request, samplePrincipal);

        assertNotNull(result);
        assertFalse(result.isCorrect());
        verify(quizMistakeRepository, times(1)).save(any());
    }

    @Test
    void completeQuiz_CalculatesScoreAndPercentage() {
        sampleQuestion.setUserAnswer(0);

        when(userRepository.findById(1L)).thenReturn(Optional.of(sampleUser));
        when(quizRepository.findByIdAndUser("quiz_123", sampleUser)).thenReturn(Optional.of(sampleQuiz));
        when(quizQuestionRepository.findByQuizOrderByIdAsc(sampleQuiz)).thenReturn(List.of(sampleQuestion));
        when(savedQuestionRepository.findByUserOrderByCreatedAtDesc(sampleUser)).thenReturn(Collections.emptyList());

        QuizResultDto result = quizService.completeQuiz("quiz_123", samplePrincipal);

        assertNotNull(result);
        assertEquals(1, result.getScore());
        assertEquals(1, result.getTotalQuestions());
        assertEquals(100.0, result.getPercentage());
        assertTrue(sampleQuiz.isCompleted());
        verify(quizRepository, times(1)).save(sampleQuiz);
    }
}
