package com.substring.docmind.chat.dto;

import com.substring.docmind.enums.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDto {

    private Long id;
    private MessageType messageType;
    private String content;
    private LocalDateTime createdAt;
}
