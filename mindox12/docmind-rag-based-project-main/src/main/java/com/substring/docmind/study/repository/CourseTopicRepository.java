package com.substring.docmind.study.repository;

import com.substring.docmind.study.entity.CourseTopic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CourseTopicRepository extends JpaRepository<CourseTopic, Long> {
    List<CourseTopic> findByCourseIdOrderByOrderIndexAsc(Long courseId);
    Optional<CourseTopic> findByIdAndCourseId(Long id, Long courseId);
}
