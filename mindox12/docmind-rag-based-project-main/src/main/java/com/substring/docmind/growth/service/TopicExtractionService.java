package com.substring.docmind.growth.service;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.growth.entity.StudyTopic;
import com.substring.docmind.user.entity.User;

import java.util.List;

public interface TopicExtractionService {

    /**
     * Scans and extracts topics for all available study files in the platform.
     */
    void scanAndExtractAllTopics();

    /**
     * Extracts topics and subtopics for a specific document.
     */
    List<StudyTopic> extractTopicsForDocument(DocumentMetadata document, User user);

    /**
     * Cleans up topics associated with a deleted document.
     */
    void deleteTopicsForDocument(String documentId);
}
