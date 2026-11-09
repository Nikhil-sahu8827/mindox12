package com.substring.docmind.study.repository;

import com.substring.docmind.study.entity.StudySessionRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface StudySessionRecordRepository extends JpaRepository<StudySessionRecord, Long> {
    List<StudySessionRecord> findByUserId(Long userId);
    List<StudySessionRecord> findByUserIdAndSessionDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    List<StudySessionRecord> findByUserIdAndSessionDate(Long userId, LocalDate sessionDate);
}
