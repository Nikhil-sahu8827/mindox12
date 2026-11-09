package com.substring.docmind.quiz.repository;

import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.quiz.entity.SavedQuestion;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SavedQuestionRepository extends JpaRepository<SavedQuestion, Long> {

    List<SavedQuestion> findByUserOrderByCreatedAtDesc(User user);

    Optional<SavedQuestion> findByUserAndQuestion(User user, QuizQuestion question);

    boolean existsByUserAndQuestion(User user, QuizQuestion question);

    void deleteByUserAndQuestion(User user, QuizQuestion question);
}
