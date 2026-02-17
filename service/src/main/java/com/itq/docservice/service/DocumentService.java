package com.itq.docservice.service;

import com.itq.docservice.dto.*;
import com.itq.docservice.entity.*;
import com.itq.docservice.exception.DocumentNotFoundException;
import com.itq.docservice.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class DocumentService {

    private final DocumentRepository documentRepository;
    private final DocumentMapper mapper;
    private final NumberGenerator numberGenerator;
    private final DocumentTransactionService txService;

    // ── Create ────────────────────────────────────────────────────────────────

    @Transactional
    public DocumentResponse createDocument(CreateDocumentRequest req) {
        Document doc = new Document();
        doc.setNumber(numberGenerator.generate());
        doc.setAuthor(req.getAuthor());
        doc.setTitle(req.getTitle());
        doc.setStatus(DocumentStatus.DRAFT);

        Document saved = documentRepository.save(doc);
        log.info("Document created: id={}, number={}", saved.getId(), saved.getNumber());
        return mapper.toResponse(saved, false);
    }

    // ── Read ──────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public DocumentResponse getDocumentWithHistory(Long id) {
        Document doc = documentRepository.findById(id)
                .orElseThrow(() -> new DocumentNotFoundException(id));
        return mapper.toResponse(doc, true);
    }

    @Transactional(readOnly = true)
    public List<DocumentResponse> getDocumentsByIds(List<Long> ids) {
        return documentRepository.findAllByIdIn(ids).stream()
                .map(doc -> mapper.toResponse(doc, false))
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<DocumentResponse> getDocumentsPaged(List<Long> ids, Pageable pageable) {
        if (ids != null && !ids.isEmpty()) {
            Specification<Document> spec = (root, query, cb) -> root.get("id").in(ids);
            return documentRepository.findAll(spec, pageable)
                    .map(doc -> mapper.toResponse(doc, false));
        }
        return documentRepository.findAll(pageable)
                .map(doc -> mapper.toResponse(doc, false));
    }

    // ── Search ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Page<DocumentResponse> search(DocumentSearchRequest req, Pageable pageable) {
        Specification<Document> spec = Specification
                .where(DocumentSpecification.hasStatus(req.getStatus()))
                .and(DocumentSpecification.hasAuthor(req.getAuthor()))
                .and(DocumentSpecification.createdAfter(req.getFrom()))
                .and(DocumentSpecification.createdBefore(req.getTo()));

        return documentRepository.findAll(spec, pageable)
                .map(doc -> mapper.toResponse(doc, false));
    }

    // ── Batch Submit ──────────────────────────────────────────────────────────
    // Each document processed in its own REQUIRES_NEW transaction via txService proxy.
    // Partial failures do not affect other documents.

    public List<BatchStatusResult> batchSubmit(BatchStatusRequest req) {
        List<BatchStatusResult> results = new ArrayList<>(req.getIds().size());
        for (Long id : req.getIds()) {
            results.add(txService.submitOne(id, req.getInitiator(), req.getComment()));
        }
        return results;
    }

    // ── Batch Approve ─────────────────────────────────────────────────────────

    public List<BatchStatusResult> batchApprove(BatchStatusRequest req) {
        List<BatchStatusResult> results = new ArrayList<>(req.getIds().size());
        for (Long id : req.getIds()) {
            results.add(txService.approveOne(id, req.getInitiator(), req.getComment()));
        }
        return results;
    }

    // ── Delegated for ConcurrentApprovalService ───────────────────────────────

    public BatchStatusResult approveOne(Long id, String initiator, String comment) {
        return txService.approveOne(id, initiator, comment);
    }
}
