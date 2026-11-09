package com.substring.docmind.chat.repository;

import com.substring.docmind.chat.entity.Conversation;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConversationRepository extends JpaRepository<Conversation, String> {

    List<Conversation> findByUserOrderByUpdatedAtDesc(User user);

    Optional<Conversation> findByIdAndUser(String id, User user);

    boolean existsByIdAndUser(String id, User user);

    void deleteByIdAndUser(String id, User user);
}
