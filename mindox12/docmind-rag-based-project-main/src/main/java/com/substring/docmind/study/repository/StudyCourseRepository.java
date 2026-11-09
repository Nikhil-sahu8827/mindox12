package com.substring.docmind.study.repository;

import com.substring.docmind.study.entity.StudyCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface StudyCourseRepository extends JpaRepository<StudyCourse, Long> {
    List<StudyCourse> findByUserId(Long userId);
    Optional<StudyCourse> findByIdAndUserId(Long id, Long userId);
}
