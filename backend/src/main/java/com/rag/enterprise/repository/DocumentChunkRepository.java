package com.rag.enterprise.repository;

import com.rag.enterprise.model.DocumentChunkEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunkEntity, Long> {
    List<DocumentChunkEntity> findByDocumentId(Long documentId);
    void deleteByDocumentId(Long documentId);
}
