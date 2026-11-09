package com.substring.docmind.chat.repository;

import com.substring.docmind.chat.entity.ChatMessage;
import com.substring.docmind.chat.entity.Conversation;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByConversationOrderByCreatedAtAsc(Conversation conversation);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt ASC")
    List<ChatMessage> findByConversationId(@Param("conversationId") String conversationId);

    @Query("SELECT m FROM ChatMessage m WHERE m.conversation.id = :conversationId ORDER BY m.createdAt DESC")
    List<ChatMessage> findRecentMessagesByConversationId(@Param("conversationId") String conversationId, Pageable pageable);

    void deleteByConversation(Conversation conversation);
}
