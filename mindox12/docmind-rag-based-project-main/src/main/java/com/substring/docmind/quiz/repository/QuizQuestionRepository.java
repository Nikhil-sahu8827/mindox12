package com.substring.docmind.quiz.repository;

import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizQuestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface QuizQuestionRepository extends JpaRepository<QuizQuestion, Long> {

    List<QuizQuestion> findByQuizOrderByIdAsc(Quiz quiz);
}
