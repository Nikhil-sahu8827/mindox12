package com.substring.docmind.quiz.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.quiz.dto.*;
import com.substring.docmind.quiz.service.QuizService;
import com.substring.docmind.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/quiz")
@RequiredArgsConstructor
@Tag(name = "AI Quiz & Learning", description = "Endpoints for generating AI quizzes from documents, scoring, and mistake review")
public class QuizController {

    private final QuizService quizService;
    private final com.substring.docmind.quiz.service.QuizPdfService quizPdfService;

    @PostMapping("/generate")
    @Operation(summary = "Generate AI Quiz", description = "Generates multiple-choice quiz questions based on document content")
    public ResponseEntity<ApiResponse<QuizDto>> generateQuiz(
            @Valid @RequestBody GenerateQuizRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to generate quiz for document {} by user id: {}", request.getDocumentId(), currentUser.getId());
        QuizDto quiz = quizService.generateQuiz(request, currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("Quiz generated successfully", quiz));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get Quiz by ID", description = "Retrieves quiz questions and options")
    public ResponseEntity<ApiResponse<QuizDto>> getQuizById(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get quiz {} by user id: {}", quizId, currentUser.getId());
        QuizDto quiz = quizService.getQuizById(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(quiz));
    }

    @PostMapping("/{id}/answer")
    @Operation(summary = "Submit Answer", description = "Submits an answer for a quiz question and evaluates correctness")
    public ResponseEntity<ApiResponse<AnswerResultDto>> submitAnswer(
            @PathVariable("id") String quizId,
            @Valid @RequestBody SubmitAnswerRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to submit answer for question {} in quiz {} by user id: {}",
                request.getQuestionId(), quizId, currentUser.getId());
        AnswerResultDto result = quizService.submitAnswer(quizId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @PostMapping("/{id}/complete")
    @Operation(summary = "Complete Quiz", description = "Finalizes quiz submission and calculates final score")
    public ResponseEntity<ApiResponse<QuizResultDto>> completeQuiz(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to complete quiz {} by user id: {}", quizId, currentUser.getId());
        QuizResultDto result = quizService.completeQuiz(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Quiz completed", result));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete Quiz", description = "Deletes a quiz and all its questions from database")
    public ResponseEntity<ApiResponse<Void>> deleteQuiz(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to delete quiz {} by user id: {}", quizId, currentUser.getId());
        quizService.deleteQuiz(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Quiz deleted successfully", null));
    }

    @GetMapping("/{id}/result")
    @Operation(summary = "Get Quiz Result", description = "Retrieves final results, breakdown, and question explanations")
    public ResponseEntity<ApiResponse<QuizResultDto>> getQuizResult(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get result for quiz {} by user id: {}", quizId, currentUser.getId());
        QuizResultDto result = quizService.getQuizResult(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/history")
    @Operation(summary = "Get Quiz History", description = "Retrieves user's quiz attempt history")
    public ResponseEntity<ApiResponse<List<QuizHistoryDto>>> getQuizHistory(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get quiz history for user id: {}", currentUser.getId());
        List<QuizHistoryDto> history = quizService.getQuizHistory(currentUser);
        return ResponseEntity.ok(ApiResponse.success(history));
    }

    @GetMapping("/progress")
    @Operation(summary = "Get Quiz Learning Progress", description = "Retrieves progress stats, average score, and mistake metrics")
    public ResponseEntity<ApiResponse<QuizProgressDto>> getQuizProgress(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get quiz progress for user id: {}", currentUser.getId());
        QuizProgressDto progress = quizService.getQuizProgress(currentUser);
        return ResponseEntity.ok(ApiResponse.success(progress));
    }

    @GetMapping("/mistakes")
    @Operation(summary = "Get Quiz Mistakes", description = "Retrieves list of questions answered incorrectly for targeted practice")
    public ResponseEntity<ApiResponse<List<QuizQuestionDto>>> getMistakes(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get mistakes for user id: {}", currentUser.getId());
        List<QuizQuestionDto> mistakes = quizService.getMistakes(currentUser);
        return ResponseEntity.ok(ApiResponse.success(mistakes));
    }

    @PostMapping("/questions/{id}/save")
    @Operation(summary = "Save / Bookmark Question", description = "Saves question for later revision")
    public ResponseEntity<ApiResponse<Void>> saveQuestion(
            @PathVariable("id") Long questionId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to save question {} for user id: {}", questionId, currentUser.getId());
        quizService.saveQuestion(questionId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Question bookmarked successfully", null));
    }

    @DeleteMapping("/questions/{id}/save")
    @Operation(summary = "Unsave / Remove Bookmark", description = "Removes question from bookmarked list")
    public ResponseEntity<ApiResponse<Void>> unsaveQuestion(
            @PathVariable("id") Long questionId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to unsave question {} for user id: {}", questionId, currentUser.getId());
        quizService.unsaveQuestion(questionId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Bookmark removed successfully", null));
    }

    @GetMapping("/questions/saved")
    @Operation(summary = "Get Saved Questions", description = "Retrieves list of bookmarked questions")
    public ResponseEntity<ApiResponse<List<QuizQuestionDto>>> getSavedQuestions(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get saved questions for user id: {}", currentUser.getId());
        List<QuizQuestionDto> saved = quizService.getSavedQuestions(currentUser);
        return ResponseEntity.ok(ApiResponse.success(saved));
    }

    @PostMapping("/questions/{id}/similar")
    @Operation(summary = "Get Similar Question", description = "Generates a practice question similar to selected question")
    public ResponseEntity<ApiResponse<QuizQuestionDto>> getSimilarQuestion(
            @PathVariable("id") Long questionId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get similar question for question id: {} by user id: {}", questionId, currentUser.getId());
        QuizQuestionDto similar = quizService.getSimilarQuestion(questionId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(similar));
    }

    @PostMapping("/questions/{id}/doubt")
    @Operation(summary = "Ask Doubt on Question", description = "Gets instant AI explanation addressing specific confusion on a question")
    public ResponseEntity<ApiResponse<DoubtResponseDto>> askQuestionDoubt(
            @PathVariable("id") Long questionId,
            @Valid @RequestBody AskDoubtRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for doubt on question {} by user id: {}", questionId, currentUser.getId());
        DoubtResponseDto answer = quizService.askQuestionDoubt(questionId, request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(answer));
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze Learning Weaknesses & Document Recommendations", description = "Analyzes performance and suggests key revision areas and question types")
    public ResponseEntity<ApiResponse<Map<String, Object>>> analyze(
            @RequestBody(required = false) List<String> documentIds,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to analyze performance for user id: {}", currentUser.getId());
        Map<String, Object> analysis = quizService.analyzeWeaknesses(documentIds, currentUser);
        return ResponseEntity.ok(ApiResponse.success(analysis));
    }

    @GetMapping("/{id}/pdf/preview")
    @Operation(summary = "Preview Quiz PDF", description = "Streams the automatically synchronized quiz PDF for inline browser preview")
    public ResponseEntity<byte[]> previewQuizPdf(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to preview quiz PDF for quiz {} by user id: {}", quizId, currentUser.getId());
        byte[] pdfBytes = quizPdfService.getPdfBytes(quizId, currentUser);
        String fileName = quizPdfService.getPdfFileName(quizId);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.inline().filename(fileName).build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping("/{id}/pdf/download")
    @Operation(summary = "Download Quiz PDF", description = "Downloads the automatically updated complete quiz PDF with all questions and answers")
    public ResponseEntity<byte[]> downloadQuizPdf(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to download quiz PDF for quiz {} by user id: {}", quizId, currentUser.getId());
        byte[] pdfBytes = quizPdfService.getPdfBytes(quizId, currentUser);
        String fileName = quizPdfService.getPdfFileName(quizId);

        org.springframework.http.HttpHeaders headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(org.springframework.http.MediaType.APPLICATION_PDF);
        headers.setContentDisposition(org.springframework.http.ContentDisposition.attachment().filename(fileName).build());
        headers.setContentLength(pdfBytes.length);

        return ResponseEntity.ok().headers(headers).body(pdfBytes);
    }

    @GetMapping("/{id}/pdf/status")
    @Operation(summary = "Get Quiz PDF Status", description = "Retrieves sync status and metadata of the quiz PDF")
    public ResponseEntity<ApiResponse<QuizPdfStatusDto>> getQuizPdfStatus(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for quiz PDF status for quiz {} by user id: {}", quizId, currentUser.getId());
        QuizPdfStatusDto status = quizPdfService.getPdfStatus(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(status));
    }

    @PostMapping("/{id}/pdf/generate")
    @Operation(summary = "Regenerate/Update Quiz PDF", description = "Manually updates or regenerates the complete PDF for this quiz")
    public ResponseEntity<ApiResponse<QuizPdfStatusDto>> regenerateQuizPdf(
            @PathVariable("id") String quizId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to regenerate PDF for quiz {} by user id: {}", quizId, currentUser.getId());
        quizPdfService.generateOrUpdatePdf(quizId);
        QuizPdfStatusDto status = quizPdfService.getPdfStatus(quizId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Quiz PDF updated successfully", status));
    }

    @PostMapping("/{id}/questions/batch")
    @Operation(summary = "Append Batch Questions", description = "Generates and appends a new batch of questions to the same quiz and updates its PDF")
    public ResponseEntity<ApiResponse<QuizDto>> appendBatchQuestions(
            @PathVariable("id") String quizId,
            @RequestParam(name = "count", defaultValue = "5") int count,
            @RequestParam(name = "difficulty", required = false) com.substring.docmind.enums.DifficultyLevel difficulty,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to append {} questions to quiz {} by user id: {}", count, quizId, currentUser.getId());
        QuizDto updatedQuiz = quizService.appendBatchQuestions(quizId, count, difficulty, currentUser);
        return ResponseEntity.ok(ApiResponse.success("New questions batch appended successfully", updatedQuiz));
    }
}
