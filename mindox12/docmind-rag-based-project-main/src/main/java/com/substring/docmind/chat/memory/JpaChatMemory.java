package com.substring.docmind.chat.memory;

import com.substring.docmind.chat.entity.ChatMessage;
import com.substring.docmind.chat.repository.ChatMessageRepository;
import com.substring.docmind.chat.repository.ConversationRepository;
import com.substring.docmind.enums.MessageType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class JpaChatMemory {

    private final ChatMessageRepository chatMessageRepository;
    private final ConversationRepository conversationRepository;

    @Transactional
    public void saveMessage(String conversationId, MessageType messageType, String content) {
        conversationRepository.findById(conversationId).ifPresent(conversation -> {
            ChatMessage message = ChatMessage.builder()
                    .conversation(conversation)
                    .messageType(messageType)
                    .content(content)
                    .createdAt(LocalDateTime.now())
                    .build();

            chatMessageRepository.save(message);
            conversation.setUpdatedAt(LocalDateTime.now());
            conversationRepository.save(conversation);
        });
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getRecentMessages(String conversationId, int limit) {
        List<ChatMessage> messages = chatMessageRepository.findRecentMessagesByConversationId(
                conversationId, PageRequest.of(0, limit)
        );
        Collections.reverse(messages);
        return messages;
    }
}
