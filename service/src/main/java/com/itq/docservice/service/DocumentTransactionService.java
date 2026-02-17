package com.itq.docservice.service;

import com.itq.docservice.dto.BatchStatusResult;
import com.itq.docservice.entity.*;
import com.itq.docservice.repository.ApprovalRegistryRepository;
import com.itq.docservice.repository.DocumentHistoryRepository;
import com.itq.docservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * Handles single-document status transitions in dedicated transactions (REQUIRES_NEW).
 * Must be a separate Spring bean so that proxy-based transaction management works correctly.
 * Calling these methods via 'this' inside DocumentService would bypass the proxy.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentTransactionService {

    private final DocumentRepository documentRepository;
    private final DocumentHistoryRepository historyRepository;
    private final ApprovalRegistryRepository registryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchStatusResult submitOne(Long id, String initiator, String comment) {
        Document doc = documentRepository.findByIdForUpdate(id).orElse(null);
        if (doc == null) {
            return new BatchStatusResult(id, BatchStatusResult.ResultCode.NOT_FOUND, "Document not found");
        }
        if (doc.getStatus() != DocumentStatus.DRAFT) {
            return new BatchStatusResult(id, BatchStatusResult.ResultCode.CONFLICT,
                    "Document is in status " + doc.getStatus() + ", expected DRAFT");
        }

        doc.setStatus(DocumentStatus.SUBMITTED);
        historyRepository.save(buildHistory(doc, initiator, DocumentAction.SUBMIT, comment));

        log.info("Document {} submitted by {}", id, initiator);
        return new BatchStatusResult(id, BatchStatusResult.ResultCode.SUCCESS, "Submitted");
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchStatusResult approveOne(Long id, String initiator, String comment) {
        try {
            Document doc = documentRepository.findByIdForUpdate(id).orElse(null);
            if (doc == null) {
                return new BatchStatusResult(id, BatchStatusResult.ResultCode.NOT_FOUND, "Document not found");
            }
            if (doc.getStatus() != DocumentStatus.SUBMITTED) {
                return new BatchStatusResult(id, BatchStatusResult.ResultCode.CONFLICT,
                        "Document is in status " + doc.getStatus() + ", expected SUBMITTED");
            }

            doc.setStatus(DocumentStatus.APPROVED);
            historyRepository.save(buildHistory(doc, initiator, DocumentAction.APPROVE, comment));

            // Registry insert — if it fails (e.g. duplicate key), exception propagates and rolls back
            ApprovalRegistry entry = new ApprovalRegistry();
            entry.setDocumentId(doc.getId());
            entry.setDocumentNumber(doc.getNumber());
            entry.setApprovedBy(initiator);
            entry.setApprovedAt(OffsetDateTime.now());
            registryRepository.saveAndFlush(entry);

            log.info("Document {} approved by {}", id, initiator);
            return new BatchStatusResult(id, BatchStatusResult.ResultCode.SUCCESS, "Approved");

        } catch (org.springframework.dao.DataIntegrityViolationException e) {
            log.error("Registry write failed for document {}: {}", id, e.getMessage());
            // REQUIRES_NEW transaction will be rolled back
            return new BatchStatusResult(id, BatchStatusResult.ResultCode.REGISTRY_ERROR,
                    "Failed to create approval registry entry");
        } catch (Exception e) {
            log.error("Error approving document {}: {}", id, e.getMessage());
            return new BatchStatusResult(id, BatchStatusResult.ResultCode.CONFLICT, e.getMessage());
        }
    }

    private DocumentHistory buildHistory(Document doc, String performedBy,
                                         DocumentAction action, String comment) {
        DocumentHistory h = new DocumentHistory();
        h.setDocument(doc);
        h.setPerformedBy(performedBy);
        h.setAction(action);
        h.setPerformedAt(OffsetDateTime.now());
        h.setComment(comment);
        return h;
    }
}
