package com.substring.docmind.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponseDto {

    private String answer;
    private String conversationId;
    @Builder.Default
    private List<CitationDto> citations = new ArrayList<>();
}
