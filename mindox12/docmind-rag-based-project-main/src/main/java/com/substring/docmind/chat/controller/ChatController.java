package com.substring.docmind.chat.controller;

import com.substring.docmind.chat.dto.ChatRequestDto;
import com.substring.docmind.chat.dto.ChatResponseDto;
import com.substring.docmind.chat.dto.SimilaritySearchRequest;
import com.substring.docmind.chat.dto.SimilaritySearchResult;
import com.substring.docmind.chat.service.RagService;
import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/chat")
@RequiredArgsConstructor
@Tag(name = "Chat & RAG", description = "Endpoints for conversational document Q&A, similarity search, and streaming")
public class ChatController {

    private final RagService ragService;

    @PostMapping("/query")
    @Operation(summary = "Ask question", description = "Answers queries using RAG context with citations and chat history")
    public ResponseEntity<ApiResponse<ChatResponseDto>> askQuestion(
            @Valid @RequestBody ChatRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to ask question: '{}' by user id: {}", request.getQuestion(), currentUser.getId());
        ChatResponseDto response = ragService.askQuestion(request, currentUser);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/search/similarity")
    @Operation(summary = "Semantic similarity search", description = "Retrieves closest document chunks to query vector")
    public ResponseEntity<ApiResponse<Map<String, Object>>> searchSimilarity(
            @Valid @RequestBody SimilaritySearchRequest request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request for similarity search: '{}' by user id: {}", request.getQuery(), currentUser.getId());
        List<SimilaritySearchResult> results = ragService.searchSimilarity(request, currentUser);
        Map<String, Object> data = new HashMap<>();
        data.put("matches", results);
        return ResponseEntity.ok(ApiResponse.success(data));
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Stream chat response", description = "Streams tokens for real-time AI response rendering")
    public ResponseEntity<ResponseBodyEmitter> streamResponse(
            @Valid @RequestBody ChatRequestDto request,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to stream chat for question: '{}' by user id: {}", request.getQuestion(), currentUser.getId());
        ResponseBodyEmitter emitter = ragService.streamResponse(request, currentUser);
        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no")
                .body(emitter);
    }
}
