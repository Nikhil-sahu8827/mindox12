package com.substring.docmind.quiz.service;

import com.substring.docmind.quiz.dto.QuizPdfStatusDto;
import com.substring.docmind.quiz.entity.QuizPdf;
import com.substring.docmind.security.UserPrincipal;

public interface QuizPdfService {

    /**
     * Generates or updates the single PDF document for the given quiz.
     * Contains all accumulated questions. Does not interrupt or throw to caller.
     */
    QuizPdf generateOrUpdatePdf(String quizId);

    /**
     * Asynchronous generation of quiz PDF.
     */
    void generateOrUpdatePdfAsync(String quizId);

    /**
     * Gets status and metadata of the quiz PDF.
     */
    QuizPdfStatusDto getPdfStatus(String quizId, UserPrincipal currentUser);

    /**
     * Retrieves the binary PDF content for preview or download.
     */
    byte[] getPdfBytes(String quizId, UserPrincipal currentUser);

    /**
     * Generates a sanitized and descriptive file name for the quiz PDF.
     */
    String getPdfFileName(String quizId);
}
