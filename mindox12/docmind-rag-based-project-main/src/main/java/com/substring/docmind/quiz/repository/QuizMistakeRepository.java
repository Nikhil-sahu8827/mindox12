package com.substring.docmind.quiz.repository;

import com.substring.docmind.quiz.entity.QuizMistake;
import com.substring.docmind.quiz.entity.QuizQuestion;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface QuizMistakeRepository extends JpaRepository<QuizMistake, Long> {

    List<QuizMistake> findByUserOrderByCreatedAtDesc(User user);

    Optional<QuizMistake> findByUserAndQuestion(User user, QuizQuestion question);

    void deleteByUserAndQuestion(User user, QuizQuestion question);
}
