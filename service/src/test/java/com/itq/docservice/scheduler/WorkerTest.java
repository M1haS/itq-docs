package com.itq.docservice.scheduler;

import com.itq.docservice.dto.BatchStatusResult;
import com.itq.docservice.entity.DocumentStatus;
import com.itq.docservice.repository.DocumentRepository;
import com.itq.docservice.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkerTest {

    @Mock private DocumentRepository documentRepository;
    @Mock private DocumentService documentService;

    @InjectMocks private SubmitWorker submitWorker;
    @InjectMocks private ApproveWorker approveWorker;

    @Test
    void submitWorker_noDraftDocs_doesNotCallBatchSubmit() {
        ReflectionTestUtils.setField(submitWorker, "batchSize", 50);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.DRAFT), any()))
                .thenReturn(new PageImpl<>(List.of()));

        submitWorker.process();

        verifyNoInteractions(documentService);
    }

    @Test
    void submitWorker_withDraftDocs_callsBatchSubmitWithCorrectIds() {
        ReflectionTestUtils.setField(submitWorker, "batchSize", 50);
        List<Long> ids = List.of(1L, 2L, 3L);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.DRAFT), any()))
                .thenReturn(new PageImpl<>(ids));
        when(documentService.batchSubmit(any())).thenReturn(
                ids.stream().map(id -> new BatchStatusResult(id, BatchStatusResult.ResultCode.SUCCESS, "ok")).toList()
        );

        submitWorker.process();

        ArgumentCaptor<com.itq.docservice.dto.BatchStatusRequest> captor =
                ArgumentCaptor.forClass(com.itq.docservice.dto.BatchStatusRequest.class);
        verify(documentService).batchSubmit(captor.capture());
        assertThat(captor.getValue().getIds()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(captor.getValue().getInitiator()).isEqualTo("submit-worker");
    }

    @Test
    void submitWorker_respectsBatchSize() {
        ReflectionTestUtils.setField(submitWorker, "batchSize", 10);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.DRAFT), any()))
                .thenReturn(new PageImpl<>(List.of()));

        submitWorker.process();

        ArgumentCaptor<PageRequest> pageCaptor = ArgumentCaptor.forClass(PageRequest.class);
        verify(documentRepository).findIdsByStatus(eq(DocumentStatus.DRAFT), pageCaptor.capture());
        assertThat(pageCaptor.getValue().getPageSize()).isEqualTo(10);
    }

    @Test
    void submitWorker_partialFailures_doNotStopProcessing() {
        ReflectionTestUtils.setField(submitWorker, "batchSize", 50);
        List<Long> ids = List.of(1L, 2L, 3L);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.DRAFT), any()))
                .thenReturn(new PageImpl<>(ids));
        when(documentService.batchSubmit(any())).thenReturn(List.of(
                new BatchStatusResult(1L, BatchStatusResult.ResultCode.SUCCESS, "ok"),
                new BatchStatusResult(2L, BatchStatusResult.ResultCode.CONFLICT, "conflict"),
                new BatchStatusResult(3L, BatchStatusResult.ResultCode.NOT_FOUND, "nf")
        ));

        // Should not throw
        submitWorker.process();

        verify(documentService).batchSubmit(any());
    }

    @Test
    void approveWorker_noSubmittedDocs_doesNotCallBatchApprove() {
        ReflectionTestUtils.setField(approveWorker, "batchSize", 50);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.SUBMITTED), any()))
                .thenReturn(new PageImpl<>(List.of()));

        approveWorker.process();

        verifyNoInteractions(documentService);
    }

    @Test
    void approveWorker_withSubmittedDocs_callsBatchApproveWithCorrectIds() {
        ReflectionTestUtils.setField(approveWorker, "batchSize", 50);
        List<Long> ids = List.of(10L, 20L);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.SUBMITTED), any()))
                .thenReturn(new PageImpl<>(ids));
        when(documentService.batchApprove(any())).thenReturn(
                ids.stream().map(id -> new BatchStatusResult(id, BatchStatusResult.ResultCode.SUCCESS, "ok")).toList()
        );

        approveWorker.process();

        ArgumentCaptor<com.itq.docservice.dto.BatchStatusRequest> captor =
                ArgumentCaptor.forClass(com.itq.docservice.dto.BatchStatusRequest.class);
        verify(documentService).batchApprove(captor.capture());
        assertThat(captor.getValue().getIds()).containsExactlyInAnyOrderElementsOf(ids);
        assertThat(captor.getValue().getInitiator()).isEqualTo("approve-worker");
    }

    @Test
    void approveWorker_exceptionFromService_doesNotPropagate() {
        ReflectionTestUtils.setField(approveWorker, "batchSize", 50);
        List<Long> ids = List.of(1L);
        when(documentRepository.findIdsByStatus(eq(DocumentStatus.SUBMITTED), any()))
                .thenReturn(new PageImpl<>(ids));
        when(documentService.batchApprove(any())).thenReturn(
                List.of(new BatchStatusResult(1L, BatchStatusResult.ResultCode.REGISTRY_ERROR, "fail"))
        );

        // Should not throw even if there's an error in results
        approveWorker.process();
    }
}
