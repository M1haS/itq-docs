package com.itq.docservice.service;

import com.itq.docservice.dto.*;
import com.itq.docservice.entity.*;
import com.itq.docservice.exception.DocumentNotFoundException;
import com.itq.docservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentMapper mapper;
    @Mock private NumberGenerator numberGenerator;
    @Mock private DocumentTransactionService txService;

    @InjectMocks
    private DocumentService documentService;

    private Document sampleDraft;
    private DocumentResponse sampleResponse;

    @BeforeEach
    void setUp() {
        sampleDraft = new Document();
        sampleDraft.setId(1L);
        sampleDraft.setNumber("DOC-20240101-1");
        sampleDraft.setAuthor("alice");
        sampleDraft.setTitle("Test Doc");
        sampleDraft.setStatus(DocumentStatus.DRAFT);
        sampleDraft.setCreatedAt(OffsetDateTime.now());
        sampleDraft.setUpdatedAt(OffsetDateTime.now());

        sampleResponse = new DocumentResponse();
        sampleResponse.setId(1L);
        sampleResponse.setNumber("DOC-20240101-1");
        sampleResponse.setStatus(DocumentStatus.DRAFT);
    }

    // ── createDocument ────────────────────────────────────────────────────────

    @Test
    void createDocument_setsFieldsCorrectly() {
        when(numberGenerator.generate()).thenReturn("DOC-20240101-1");
        when(documentRepository.save(any())).thenReturn(sampleDraft);
        when(mapper.toResponse(sampleDraft, false)).thenReturn(sampleResponse);

        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setAuthor("alice");
        req.setTitle("Test Doc");

        DocumentResponse result = documentService.createDocument(req);

        assertThat(result.getNumber()).isEqualTo("DOC-20240101-1");
        assertThat(result.getStatus()).isEqualTo(DocumentStatus.DRAFT);

        verify(documentRepository).save(argThat(d ->
                d.getAuthor().equals("alice") &&
                d.getTitle().equals("Test Doc") &&
                d.getStatus() == DocumentStatus.DRAFT
        ));
    }

    // ── getDocumentWithHistory ────────────────────────────────────────────────

    @Test
    void getDocumentWithHistory_returnsDocument() {
        when(documentRepository.findById(1L)).thenReturn(Optional.of(sampleDraft));
        when(mapper.toResponse(sampleDraft, true)).thenReturn(sampleResponse);

        DocumentResponse result = documentService.getDocumentWithHistory(1L);

        assertThat(result).isNotNull();
        verify(mapper).toResponse(sampleDraft, true);
    }

    @Test
    void getDocumentWithHistory_throwsWhenNotFound() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> documentService.getDocumentWithHistory(999L))
                .isInstanceOf(DocumentNotFoundException.class)
                .hasMessageContaining("999");
    }

    // ── getDocumentsByIds ─────────────────────────────────────────────────────

    @Test
    void getDocumentsByIds_returnsListForFoundIds() {
        when(documentRepository.findAllByIdIn(List.of(1L, 2L)))
                .thenReturn(List.of(sampleDraft));
        when(mapper.toResponse(sampleDraft, false)).thenReturn(sampleResponse);

        List<DocumentResponse> result = documentService.getDocumentsByIds(List.of(1L, 2L));

        assertThat(result).hasSize(1);
    }

    // ── getDocumentsPaged ─────────────────────────────────────────────────────

    @Test
    void getDocumentsPaged_withoutIds_returnsAllPaged() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> page = new PageImpl<>(List.of(sampleDraft));
        when(documentRepository.findAll(pageable)).thenReturn(page);
        when(mapper.toResponse(sampleDraft, false)).thenReturn(sampleResponse);

        Page<DocumentResponse> result = documentService.getDocumentsPaged(null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(documentRepository).findAll(pageable);
    }

    @Test
    void getDocumentsPaged_withIds_usesSpecification() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> page = new PageImpl<>(List.of(sampleDraft));
        when(documentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(mapper.toResponse(sampleDraft, false)).thenReturn(sampleResponse);

        Page<DocumentResponse> result = documentService.getDocumentsPaged(List.of(1L), pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(documentRepository).findAll(any(Specification.class), eq(pageable));
    }

    // ── batchSubmit ───────────────────────────────────────────────────────────

    @Test
    void batchSubmit_delegatesToTxService_forEachId() {
        BatchStatusResult ok = new BatchStatusResult(1L, BatchStatusResult.ResultCode.SUCCESS, "ok");
        BatchStatusResult nf = new BatchStatusResult(2L, BatchStatusResult.ResultCode.NOT_FOUND, "nf");
        when(txService.submitOne(1L, "bob", null)).thenReturn(ok);
        when(txService.submitOne(2L, "bob", null)).thenReturn(nf);

        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(1L, 2L));
        req.setInitiator("bob");

        List<BatchStatusResult> results = documentService.batchSubmit(req);

        assertThat(results).hasSize(2);
        assertThat(results.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        assertThat(results.get(1).getResult()).isEqualTo(BatchStatusResult.ResultCode.NOT_FOUND);
        verify(txService).submitOne(1L, "bob", null);
        verify(txService).submitOne(2L, "bob", null);
    }

    // ── batchApprove ──────────────────────────────────────────────────────────

    @Test
    void batchApprove_delegatesToTxService_forEachId() {
        BatchStatusResult ok = new BatchStatusResult(1L, BatchStatusResult.ResultCode.SUCCESS, "ok");
        when(txService.approveOne(1L, "carol", "note")).thenReturn(ok);

        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(1L));
        req.setInitiator("carol");
        req.setComment("note");

        List<BatchStatusResult> results = documentService.batchApprove(req);

        assertThat(results).hasSize(1);
        assertThat(results.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        verify(txService).approveOne(1L, "carol", "note");
    }

    // ── search ────────────────────────────────────────────────────────────────

    @Test
    void search_passesSpecificationToRepository() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Document> page = new PageImpl<>(List.of(sampleDraft));
        when(documentRepository.findAll(any(Specification.class), eq(pageable))).thenReturn(page);
        when(mapper.toResponse(sampleDraft, false)).thenReturn(sampleResponse);

        DocumentSearchRequest searchReq = new DocumentSearchRequest();
        searchReq.setStatus(DocumentStatus.DRAFT);
        searchReq.setAuthor("alice");

        Page<DocumentResponse> result = documentService.search(searchReq, pageable);

        assertThat(result.getContent()).hasSize(1);
    }
}
