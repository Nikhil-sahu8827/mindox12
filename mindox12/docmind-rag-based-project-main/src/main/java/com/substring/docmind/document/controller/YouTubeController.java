package com.substring.docmind.document.controller;

import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.document.dto.youtube.*;
import com.substring.docmind.document.dto.youtube.YouTubeChatDto.*;
import com.substring.docmind.document.dto.youtube.YouTubeLearningMaterialsDto.*;
import com.substring.docmind.document.service.YouTubeService;
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
@RequestMapping("/api/v1/youtube")
@RequiredArgsConstructor
@Tag(name = "YouTube Video Intelligence", description = "Endpoints for extracting complete YouTube video transcripts, timestamps, summaries, notes, quizzes, and grounded video chat")
public class YouTubeController {

    private final YouTubeService youTubeService;

    @PostMapping("/process")
    @Operation(summary = "Process YouTube Video", description = "Extracts complete transcript, timestamps, metadata, and generates vector chunks")
    public ResponseEntity<ApiResponse<YouTubeVideoDataDto>> processYouTube(
            @Valid @RequestBody ProcessYouTubeRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to process YouTube video URL '{}' by user id: {}", request.getYoutubeUrl(), currentUser.getId());
        YouTubeVideoDataDto response = youTubeService.processYouTubeUrl(request.getYoutubeUrl(), request.getLanguage(), currentUser);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success("YouTube video processed successfully", response));
    }

    @GetMapping("/{id}/data")
    @Operation(summary = "Get YouTube Video Data", description = "Retrieves video details, metadata, status, and transcript preview")
    public ResponseEntity<ApiResponse<YouTubeVideoDataDto>> getVideoData(
            @PathVariable("id") String id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get YouTube video data for id '{}' by user id: {}", id, currentUser.getId());
        YouTubeVideoDataDto response = youTubeService.getVideoData(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/summary")
    @Operation(summary = "Get AI Video Summary", description = "Generates a structured video summary with timestamps from the complete transcript")
    public ResponseEntity<ApiResponse<Map<String, String>>> getVideoSummary(
            @PathVariable("id") String id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for YouTube video summary for id '{}' by user id: {}", id, currentUser.getId());
        String summary = youTubeService.getVideoSummary(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(Map.of("summary", summary)));
    }

    @GetMapping("/{id}/notes")
    @Operation(summary = "Get Video Lecture Notes & Explanations", description = "Generates topic-wise notes, definitions, concept explanations, and flashcards")
    public ResponseEntity<ApiResponse<YouTubeLearningMaterialsDto>> getVideoNotes(
            @PathVariable("id") String id,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for YouTube video notes for id '{}' by user id: {}", id, currentUser.getId());
        YouTubeLearningMaterialsDto response = youTubeService.getVideoNotes(id, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{id}/questions")
    @Operation(summary = "Generate Questions from Video", description = "Generates conceptual, recall, and critical thinking questions strictly from the transcript")
    public ResponseEntity<ApiResponse<List<VideoQuestion>>> getVideoQuestions(
            @PathVariable("id") String id,
            @RequestParam(name = "count", defaultValue = "8") int count,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for YouTube video questions for id '{}' by user id: {}", id, currentUser.getId());
        List<VideoQuestion> questions = youTubeService.getVideoQuestions(id, count, currentUser);
        return ResponseEntity.ok(ApiResponse.success(questions));
    }

    @GetMapping("/{id}/quiz")
    @Operation(summary = "Generate Video Quiz", description = "Generates MCQs and True/False questions with explanations and video timestamps")
    public ResponseEntity<ApiResponse<List<VideoQuizQuestion>>> getVideoQuiz(
            @PathVariable("id") String id,
            @RequestParam(name = "count", defaultValue = "8") int count,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for YouTube video quiz for id '{}' by user id: {}", id, currentUser.getId());
        List<VideoQuizQuestion> quiz = youTubeService.getVideoQuiz(id, count, currentUser);
        return ResponseEntity.ok(ApiResponse.success(quiz));
    }

    @PostMapping("/chat")
    @Operation(summary = "Chat with Video AI", description = "Answers questions strictly grounded in the complete video transcript with timestamp citations")
    public ResponseEntity<ApiResponse<Response>> chatWithVideo(
            @Valid @RequestBody Request request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to chat with YouTube video '{}' by user id: {}", request.getQuestion(), currentUser.getId());
        Response response = youTubeService.chatWithVideo(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
