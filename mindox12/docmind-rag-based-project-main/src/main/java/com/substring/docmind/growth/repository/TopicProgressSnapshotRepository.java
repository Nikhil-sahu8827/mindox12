package com.substring.docmind.growth.repository;

import com.substring.docmind.growth.entity.TopicProgressSnapshot;
import com.substring.docmind.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TopicProgressSnapshotRepository extends JpaRepository<TopicProgressSnapshot, Long> {

    List<TopicProgressSnapshot> findByUserOrderBySnapshotDateAsc(User user);

    List<TopicProgressSnapshot> findByUserAndTopicNameOrderBySnapshotDateAsc(User user, String topicName);

    Optional<TopicProgressSnapshot> findByUserAndTopicNameAndSnapshotDate(User user, String topicName, LocalDate snapshotDate);

    List<TopicProgressSnapshot> findByUserAndSnapshotDateGreaterThanEqualOrderBySnapshotDateAsc(User user, LocalDate date);
}
