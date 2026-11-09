package com.substring.docmind.quiz.repository;

import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizRepository extends JpaRepository<Quiz, String> {

    List<Quiz> findByUserOrderByCreatedAtDesc(User user);

    Optional<Quiz> findByIdAndUser(String id, User user);

    @Query("SELECT COUNT(q) FROM Quiz q WHERE q.user = :user AND q.completed = true")
    long countCompletedByUser(@Param("user") User user);

    @Query("SELECT AVG(q.score) FROM Quiz q WHERE q.user = :user AND q.completed = true")
    Double calculateAverageScoreByUser(@Param("user") User user);

    @Modifying
    @Transactional
    @Query(value = "UPDATE quizzes SET document_id = NULL WHERE document_id = :documentId", nativeQuery = true)
    void unlinkDocument(@Param("documentId") String documentId);
}
