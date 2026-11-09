package com.substring.docmind.chat.service;

import com.substring.docmind.chat.dto.ChatRequestDto;
import com.substring.docmind.chat.dto.ChatResponseDto;
import com.substring.docmind.chat.dto.SimilaritySearchRequest;
import com.substring.docmind.chat.dto.SimilaritySearchResult;
import com.substring.docmind.security.UserPrincipal;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.List;

public interface RagService {

    ChatResponseDto askQuestion(ChatRequestDto request, UserPrincipal currentUser);

    List<SimilaritySearchResult> searchSimilarity(SimilaritySearchRequest request, UserPrincipal currentUser);

    ResponseBodyEmitter streamResponse(ChatRequestDto request, UserPrincipal currentUser);
}
