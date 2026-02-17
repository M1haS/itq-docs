package com.itq.docservice.service;

import com.itq.docservice.dto.BatchStatusResult;
import com.itq.docservice.entity.*;
import com.itq.docservice.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentTransactionServiceTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentHistoryRepository historyRepository;
    @Mock private ApprovalRegistryRepository registryRepository;

    @InjectMocks
    private DocumentTransactionService txService;

    private Document draftDoc;
    private Document submittedDoc;

    @BeforeEach
    void setUp() {
        draftDoc = new Document();
        draftDoc.setId(1L);
        draftDoc.setNumber("DOC-001");
        draftDoc.setAuthor("alice");
        draftDoc.setTitle("Test");
        draftDoc.setStatus(DocumentStatus.DRAFT);
        draftDoc.setCreatedAt(OffsetDateTime.now());
        draftDoc.setUpdatedAt(OffsetDateTime.now());

        submittedDoc = new Document();
        submittedDoc.setId(2L);
        submittedDoc.setNumber("DOC-002");
        submittedDoc.setAuthor("bob");
        submittedDoc.setTitle("Test 2");
        submittedDoc.setStatus(DocumentStatus.SUBMITTED);
        submittedDoc.setCreatedAt(OffsetDateTime.now());
        submittedDoc.setUpdatedAt(OffsetDateTime.now());
    }

    // ── submitOne ─────────────────────────────────────────────────────────────

    @Test
    void submitOne_success_changesDraftToSubmitted() {
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDoc));

        BatchStatusResult result = txService.submitOne(1L, "bob", "go");

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        assertThat(draftDoc.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
        verify(historyRepository).save(argThat(h ->
                h.getAction() == DocumentAction.SUBMIT &&
                h.getPerformedBy().equals("bob") &&
                "go".equals(h.getComment())
        ));
    }

    @Test
    void submitOne_notFound_returnsNotFound() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        BatchStatusResult result = txService.submitOne(99L, "bob", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.NOT_FOUND);
        verifyNoInteractions(historyRepository);
    }

    @Test
    void submitOne_alreadySubmitted_returnsConflict() {
        when(documentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(submittedDoc));

        BatchStatusResult result = txService.submitOne(2L, "bob", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
        assertThat(result.getMessage()).contains("SUBMITTED");
        verifyNoInteractions(historyRepository);
    }

    @Test
    void submitOne_alreadyApproved_returnsConflict() {
        draftDoc.setStatus(DocumentStatus.APPROVED);
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDoc));

        BatchStatusResult result = txService.submitOne(1L, "bob", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    @Test
    void submitOne_nullComment_isAllowed() {
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDoc));

        BatchStatusResult result = txService.submitOne(1L, "bob", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        verify(historyRepository).save(argThat(h -> h.getComment() == null));
    }

    // ── approveOne ────────────────────────────────────────────────────────────

    @Test
    void approveOne_success_changesSubmittedToApproved() {
        when(documentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(submittedDoc));
        when(registryRepository.saveAndFlush(any())).thenAnswer(inv -> inv.getArgument(0));

        BatchStatusResult result = txService.approveOne(2L, "carol", "lgtm");

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        assertThat(submittedDoc.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        verify(historyRepository).save(argThat(h ->
                h.getAction() == DocumentAction.APPROVE &&
                h.getPerformedBy().equals("carol")
        ));
        verify(registryRepository).saveAndFlush(argThat(r ->
                r.getDocumentId().equals(2L) &&
                r.getApprovedBy().equals("carol")
        ));
    }

    @Test
    void approveOne_notFound_returnsNotFound() {
        when(documentRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        BatchStatusResult result = txService.approveOne(99L, "carol", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.NOT_FOUND);
        verifyNoInteractions(historyRepository, registryRepository);
    }

    @Test
    void approveOne_draftStatus_returnsConflict() {
        when(documentRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(draftDoc));

        BatchStatusResult result = txService.approveOne(1L, "carol", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
        assertThat(result.getMessage()).contains("DRAFT");
        verifyNoInteractions(historyRepository, registryRepository);
    }

    @Test
    void approveOne_registryFails_returnsRegistryError() {
        when(documentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(submittedDoc));
        when(registryRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("duplicate key"));

        BatchStatusResult result = txService.approveOne(2L, "carol", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.REGISTRY_ERROR);
        assertThat(result.getMessage()).contains("registry");
    }

    @Test
    void approveOne_registryFails_documentStatusNotChanged() {
        // Status change happens in-memory; rollback is handled by Spring @Transactional.
        // Here we verify the result code is REGISTRY_ERROR so the caller knows to report it.
        when(documentRepository.findByIdForUpdate(2L)).thenReturn(Optional.of(submittedDoc));
        when(registryRepository.saveAndFlush(any()))
                .thenThrow(new DataIntegrityViolationException("constraint"));

        BatchStatusResult result = txService.approveOne(2L, "carol", null);

        assertThat(result.getResult()).isEqualTo(BatchStatusResult.ResultCode.REGISTRY_ERROR);
    }
}
