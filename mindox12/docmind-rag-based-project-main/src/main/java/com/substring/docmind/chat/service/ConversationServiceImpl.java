package com.substring.docmind.chat.service;

import com.substring.docmind.chat.dto.ChatMessageDto;
import com.substring.docmind.chat.dto.ConversationDto;
import com.substring.docmind.chat.entity.Conversation;
import com.substring.docmind.chat.repository.ChatMessageRepository;
import com.substring.docmind.chat.repository.ConversationRepository;
import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationServiceImpl implements ConversationService {

    private final ConversationRepository conversationRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional(readOnly = true)
    public List<ConversationDto> getUserConversations(UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        return conversationRepository.findByUserOrderByUpdatedAtDesc(user).stream()
                .map(this::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(readOnly = true)
    public ConversationDto getConversationById(String conversationId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        return toDtoWithMessages(conversation);
    }

    @Override
    @Transactional
    public Conversation getOrCreateConversation(String conversationId, String firstPrompt, User user) {
        if (conversationId != null && !conversationId.isBlank()) {
            return conversationRepository.findByIdAndUser(conversationId, user)
                    .orElseGet(() -> createNewConversation(conversationId, firstPrompt, user));
        }
        return createNewConversation(UUID.randomUUID().toString(), firstPrompt, user);
    }

    @Override
    @Transactional
    public void deleteConversation(String conversationId, UserPrincipal currentUser) {
        User user = getUser(currentUser.getId());
        Conversation conversation = conversationRepository.findByIdAndUser(conversationId, user)
                .orElseThrow(() -> new ResourceNotFoundException("Conversation", "id", conversationId));

        conversationRepository.delete(conversation);
        log.info("Conversation {} deleted for user {}", conversationId, user.getId());
    }

    private Conversation createNewConversation(String id, String firstPrompt, User user) {
        String title = firstPrompt != null && !firstPrompt.isBlank()
                ? (firstPrompt.length() > 60 ? firstPrompt.substring(0, 57) + "..." : firstPrompt)
                : "New Conversation";

        Conversation conversation = Conversation.builder()
                .id(id)
                .user(user)
                .title(title)
                .build();

        return conversationRepository.save(conversation);
    }

    private ConversationDto toDto(Conversation conversation) {
        return ConversationDto.builder()
                .id(conversation.getId())
                .title(conversation.getTitle())
                .createdAt(conversation.getCreatedAt())
                .updatedAt(conversation.getUpdatedAt())
                .build();
    }

    private ConversationDto toDtoWithMessages(Conversation conversation) {
        List<ChatMessageDto> messages = chatMessageRepository.findByConversationOrderByCreatedAtAsc(conversation).stream()
                .map(m -> ChatMessageDto.builder()
                        .id(m.getId())
                        .messageType(m.getMessageType())
                        .content(m.getContent())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        ConversationDto dto = toDto(conversation);
        dto.setMessages(messages);
        return dto;
    }

    private User getUser(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }
}
