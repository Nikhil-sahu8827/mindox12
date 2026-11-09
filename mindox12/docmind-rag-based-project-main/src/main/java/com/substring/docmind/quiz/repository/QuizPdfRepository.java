package com.substring.docmind.quiz.repository;

import com.substring.docmind.quiz.entity.Quiz;
import com.substring.docmind.quiz.entity.QuizPdf;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface QuizPdfRepository extends JpaRepository<QuizPdf, Long> {

    Optional<QuizPdf> findByQuiz(Quiz quiz);

    @Query("SELECT qp FROM QuizPdf qp WHERE qp.quiz.id = :quizId")
    Optional<QuizPdf> findByQuizId(@Param("quizId") String quizId);

    boolean existsByQuizId(String quizId);
}
