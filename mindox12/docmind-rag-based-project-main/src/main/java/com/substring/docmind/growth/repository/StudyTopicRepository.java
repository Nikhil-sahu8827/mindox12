package com.substring.docmind.growth.repository;

import com.substring.docmind.growth.entity.StudyTopic;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyTopicRepository extends JpaRepository<StudyTopic, Long> {

    List<StudyTopic> findByDocumentIdOrderByIdAsc(String documentId);

    @Query("SELECT t FROM StudyTopic t WHERE t.isGlobal = true OR t.user.id = :userId ORDER BY t.sourceFileName ASC, t.orderIndex ASC, t.id ASC")
    List<StudyTopic> findAllForUser(@Param("userId") Long userId);

    @Query("SELECT t FROM StudyTopic t WHERE (t.isGlobal = true OR t.user.id = :userId) AND t.hierarchyLevel = 1 ORDER BY t.sourceFileName ASC, t.orderIndex ASC")
    List<StudyTopic> findMainTopicsForUser(@Param("userId") Long userId);

    List<StudyTopic> findByParentTopicIdOrderByIdAsc(Long parentTopicId);

    Optional<StudyTopic> findByNormalizedKey(String normalizedKey);

    Optional<StudyTopic> findFirstByNormalizedKeyOrderByIdAsc(String normalizedKey);

    Optional<StudyTopic> findByNormalizedKeyAndUser(String normalizedKey, User user);

    Optional<StudyTopic> findFirstByNormalizedKeyAndUserOrderByIdAsc(String normalizedKey, User user);

    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.transaction.annotation.Transactional
    @Query(value = "DELETE FROM study_topics WHERE document_id = :documentId", nativeQuery = true)
    void deleteByDocumentId(@Param("documentId") String documentId);

    void deleteBySourceFileName(String sourceFileName);
}
