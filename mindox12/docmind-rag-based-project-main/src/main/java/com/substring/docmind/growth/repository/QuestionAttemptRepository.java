package com.substring.docmind.growth.repository;

import com.substring.docmind.growth.entity.QuestionAttempt;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface QuestionAttemptRepository extends JpaRepository<QuestionAttempt, Long> {

    List<QuestionAttempt> findByUserOrderByCreatedAtDesc(User user);

    List<QuestionAttempt> findByUserAndCreatedAtAfterOrderByCreatedAtAsc(User user, LocalDateTime after);

    List<QuestionAttempt> findByUserAndTopicNameOrderByCreatedAtDesc(User user, String topicName);

    List<QuestionAttempt> findByUserAndDocumentIdOrderByCreatedAtDesc(User user, String documentId);

    @Query("SELECT COUNT(DISTINCT a.question.id) FROM QuestionAttempt a WHERE a.user.id = :userId")
    long countUniqueQuestionsAttemptedByUser(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT a.question.id) FROM QuestionAttempt a WHERE a.user.id = :userId AND LOWER(a.topicName) = LOWER(:topicName)")
    long countUniqueQuestionsAttemptedByUserAndTopic(@Param("userId") Long userId, @Param("topicName") String topicName);

    @Query("SELECT COUNT(DISTINCT a.question.id) FROM QuestionAttempt a WHERE a.user.id = :userId AND a.documentId = :documentId")
    long countUniqueQuestionsAttemptedByUserAndDocument(@Param("userId") Long userId, @Param("documentId") String documentId);

    long countByUser(User user);

    long countByUserAndIsCorrectTrue(User user);
}
