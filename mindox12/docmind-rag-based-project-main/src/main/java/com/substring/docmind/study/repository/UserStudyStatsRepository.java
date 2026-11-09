package com.substring.docmind.study.repository;

import com.substring.docmind.study.entity.UserStudyStats;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserStudyStatsRepository extends JpaRepository<UserStudyStats, Long> {
    Optional<UserStudyStats> findByUserId(Long userId);
}
