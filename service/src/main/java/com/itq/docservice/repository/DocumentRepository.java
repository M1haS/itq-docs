package com.itq.docservice.repository;

import com.itq.docservice.entity.Document;
import com.itq.docservice.entity.DocumentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long>, JpaSpecificationExecutor<Document> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM Document d WHERE d.id = :id")
    Optional<Document> findByIdForUpdate(@Param("id") Long id);

    @Query("SELECT d.id FROM Document d WHERE d.status = :status")
    Page<Long> findIdsByStatus(@Param("status") DocumentStatus status, Pageable pageable);

    List<Document> findAllByIdIn(List<Long> ids);
}
