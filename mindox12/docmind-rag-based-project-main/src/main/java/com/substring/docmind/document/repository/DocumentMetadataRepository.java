package com.substring.docmind.document.repository;

import com.substring.docmind.document.entity.DocumentMetadata;
import com.substring.docmind.user.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentMetadataRepository extends JpaRepository<DocumentMetadata, String> {

    List<DocumentMetadata> findByUserOrderByCreatedAtDesc(User user);

    Page<DocumentMetadata> findByUser(User user, Pageable pageable);

    Optional<DocumentMetadata> findByIdAndUser(String id, User user);

    boolean existsByIdAndUser(String id, User user);

    void deleteByIdAndUser(String id, User user);

    long countByUser(User user);
}
