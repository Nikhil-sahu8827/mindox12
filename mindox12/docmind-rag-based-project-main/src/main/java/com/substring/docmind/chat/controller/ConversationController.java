package com.substring.docmind.chat.controller;

import com.substring.docmind.chat.dto.ConversationDto;
import com.substring.docmind.chat.service.ConversationService;
import com.substring.docmind.common.ApiResponse;
import com.substring.docmind.security.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/v1/conversations")
@RequiredArgsConstructor
@Tag(name = "Conversation History", description = "Endpoints for retrieving and managing chat history")
public class ConversationController {

    private final ConversationService conversationService;

    @GetMapping
    @Operation(summary = "Get user conversations", description = "Retrieves conversation sessions owned by current user")
    public ResponseEntity<ApiResponse<List<ConversationDto>>> getUserConversations(
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to list conversations for user id: {}", currentUser.getId());
        List<ConversationDto> conversations = conversationService.getUserConversations(currentUser);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get conversation by ID", description = "Retrieves conversation details and full message history")
    public ResponseEntity<ApiResponse<ConversationDto>> getConversationById(
            @PathVariable("id") String conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to get conversation {} for user id: {}", conversationId, currentUser.getId());
        ConversationDto conversation = conversationService.getConversationById(conversationId, currentUser);
        return ResponseEntity.ok(ApiResponse.success(conversation));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete conversation", description = "Deletes conversation and associated chat messages")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable("id") String conversationId,
            @AuthenticationPrincipal UserPrincipal currentUser
    ) {
        log.info("REST request to delete conversation {} for user id: {}", conversationId, currentUser.getId());
        conversationService.deleteConversation(conversationId, currentUser);
        return ResponseEntity.ok(ApiResponse.success("Conversation deleted successfully", null));
    }
}
