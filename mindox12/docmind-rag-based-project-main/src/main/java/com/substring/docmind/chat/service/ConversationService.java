package com.substring.docmind.chat.service;

import com.substring.docmind.chat.dto.ConversationDto;
import com.substring.docmind.chat.entity.Conversation;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.entity.User;

import java.util.List;

public interface ConversationService {

    List<ConversationDto> getUserConversations(UserPrincipal currentUser);

    ConversationDto getConversationById(String conversationId, UserPrincipal currentUser);

    Conversation getOrCreateConversation(String conversationId, String firstPrompt, User user);

    void deleteConversation(String conversationId, UserPrincipal currentUser);
}
