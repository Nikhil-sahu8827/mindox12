package com.substring.docmind.user.service;

import com.substring.docmind.exception.ResourceNotFoundException;
import com.substring.docmind.security.UserPrincipal;
import com.substring.docmind.user.dto.UserDto;
import com.substring.docmind.user.entity.User;
import com.substring.docmind.user.mapper.UserMapper;
import com.substring.docmind.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {

    private final UserRepository userRepository;
    private final UserMapper userMapper;
    private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional(readOnly = true)
    public UserDto getCurrentUserProfile(Long userId) {
        log.info("Fetching user profile for id: {}", userId);
        User user = getUserById(userId);
        return userMapper.toDto(user);
    }

    @Override
    @Transactional(readOnly = true)
    public User getUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
    }

    @Override
    @Transactional(readOnly = true)
    public List<UserDto> getAllUsers() {
        return userRepository.findAll().stream()
                .map(userMapper::toDto)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteUser(Long userId, UserPrincipal currentUser) {
        boolean isAdmin = currentUser.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));

        if (!isAdmin && !currentUser.getId().equals(userId)) {
            throw new AccessDeniedException("You are not authorized to delete this user account");
        }

        User user = getUserById(userId);
        log.info("Deleting user id {} ('{}') and all associated database records", userId, user.getUsername());

        // 1. Delete vector embeddings from vector_store (if table exists)
        try {
            if (isVectorStoreTablePresent()) {
                jdbcTemplate.update(
                        "DELETE FROM vector_store WHERE metadata->>'userId' = ? " +
                        "OR metadata->>'documentId' IN (SELECT id FROM document_metadata WHERE user_id = ?)",
                        String.valueOf(userId), userId
                );
            } else {
                log.info("Table 'vector_store' does not exist in database, skipping vector deletion for user: {}", userId);
            }
        } catch (Exception e) {
            log.warn("Vector store cleanup warning for user {}: {}", userId, e.getMessage());
        }

        // 2. Delete study tracking records & courses
        jdbcTemplate.update("DELETE FROM course_topics WHERE course_id IN (SELECT id FROM study_courses WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM study_courses WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM study_session_records WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM user_study_stats WHERE user_id = ?", userId);

        // 3. Delete topic growth & progress snapshots
        jdbcTemplate.update("DELETE FROM question_attempts WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM topic_progress_snapshots WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM study_topics WHERE user_id = ?", userId);

        // 4. Delete quiz data (saved questions, mistakes, pdfs, options, questions, quizzes)
        jdbcTemplate.update("DELETE FROM saved_questions WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM quiz_mistakes WHERE user_id = ?", userId);
        jdbcTemplate.update("DELETE FROM quiz_pdfs WHERE quiz_id IN (SELECT id FROM quizzes WHERE user_id = ?)", userId);
        jdbcTemplate.update(
                "DELETE FROM quiz_question_options WHERE question_id IN (" +
                "  SELECT qq.id FROM quiz_questions qq JOIN quizzes q ON qq.quiz_id = q.id WHERE q.user_id = ?" +
                ")", userId
        );
        jdbcTemplate.update("DELETE FROM quiz_questions WHERE quiz_id IN (SELECT id FROM quizzes WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM quizzes WHERE user_id = ?", userId);

        // 5. Delete chat messages & conversations
        jdbcTemplate.update("DELETE FROM chat_messages WHERE conversation_id IN (SELECT id FROM conversations WHERE user_id = ?)", userId);
        jdbcTemplate.update("DELETE FROM conversations WHERE user_id = ?", userId);

        // 6. Delete document metadata
        jdbcTemplate.update("DELETE FROM document_metadata WHERE user_id = ?", userId);

        // 7. Finally delete the user from users table
        userRepository.delete(user);
        userRepository.flush();

        log.info("User id {} and all associated database records successfully deleted from PostgreSQL", userId);
    }

    private boolean isVectorStoreTablePresent() {
        if (jdbcTemplate == null) return false;
        try {
            Boolean exists = jdbcTemplate.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM information_schema.tables WHERE table_schema = 'public' AND table_name = 'vector_store')",
                    Boolean.class
            );
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            return false;
        }
    }
}
