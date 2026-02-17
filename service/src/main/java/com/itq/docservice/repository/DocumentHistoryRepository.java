package com.itq.docservice.repository;

import com.itq.docservice.entity.DocumentHistory;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DocumentHistoryRepository extends JpaRepository<DocumentHistory, Long> {
}
