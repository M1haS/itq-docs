package com.itq.docservice;

import com.itq.docservice.dto.*;
import com.itq.docservice.entity.DocumentStatus;
import com.itq.docservice.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DocumentServiceIntegrationTest {

    @Autowired
    private DocumentService documentService;

    // ── Happy path: create → submit → approve ────────────────────────────────

    @Test
    void happyPath_singleDocument() {
        // Create
        CreateDocumentRequest createReq = new CreateDocumentRequest();
        createReq.setAuthor("alice");
        createReq.setTitle("Test Document");
        DocumentResponse created = documentService.createDocument(createReq);

        assertThat(created.getId()).isNotNull();
        assertThat(created.getNumber()).startsWith("DOC-");
        assertThat(created.getStatus()).isEqualTo(DocumentStatus.DRAFT);

        // Submit
        BatchStatusRequest submitReq = new BatchStatusRequest();
        submitReq.setIds(List.of(created.getId()));
        submitReq.setInitiator("bob");
        List<BatchStatusResult> submitResults = documentService.batchSubmit(submitReq);

        assertThat(submitResults).hasSize(1);
        assertThat(submitResults.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        // Verify SUBMITTED
        DocumentResponse afterSubmit = documentService.getDocumentWithHistory(created.getId());
        assertThat(afterSubmit.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
        assertThat(afterSubmit.getHistory()).hasSize(1);
        assertThat(afterSubmit.getHistory().get(0).getPerformedBy()).isEqualTo("bob");

        // Approve
        BatchStatusRequest approveReq = new BatchStatusRequest();
        approveReq.setIds(List.of(created.getId()));
        approveReq.setInitiator("carol");
        List<BatchStatusResult> approveResults = documentService.batchApprove(approveReq);

        assertThat(approveResults).hasSize(1);
        assertThat(approveResults.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        // Verify APPROVED with full history
        DocumentResponse afterApprove = documentService.getDocumentWithHistory(created.getId());
        assertThat(afterApprove.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(afterApprove.getHistory()).hasSize(2);
    }

    // ── Batch submit with partial results ─────────────────────────────────────

    @Test
    void batchSubmit_partialResults() {
        // Create one valid document
        CreateDocumentRequest createReq = new CreateDocumentRequest();
        createReq.setAuthor("alice");
        createReq.setTitle("Valid Doc");
        DocumentResponse created = documentService.createDocument(createReq);

        // Batch submit: valid id + non-existent id
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(created.getId(), 99999L));
        req.setInitiator("submitter");

        List<BatchStatusResult> results = documentService.batchSubmit(req);

        assertThat(results).hasSize(2);

        BatchStatusResult success = results.stream()
                .filter(r -> r.getId().equals(created.getId())).findFirst().orElseThrow();
        assertThat(success.getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        BatchStatusResult notFound = results.stream()
                .filter(r -> r.getId().equals(99999L)).findFirst().orElseThrow();
        assertThat(notFound.getResult()).isEqualTo(BatchStatusResult.ResultCode.NOT_FOUND);
    }

    // ── Batch approve with partial results ────────────────────────────────────

    @Test
    void batchApprove_partialResults_includesConflict() {
        // Doc1 - will be SUBMITTED (valid for approve)
        CreateDocumentRequest req1 = new CreateDocumentRequest();
        req1.setAuthor("alice");
        req1.setTitle("Doc for approve");
        DocumentResponse doc1 = documentService.createDocument(req1);

        BatchStatusRequest submitReq = new BatchStatusRequest();
        submitReq.setIds(List.of(doc1.getId()));
        submitReq.setInitiator("submitter");
        documentService.batchSubmit(submitReq);

        // Doc2 - stays DRAFT (conflict for approve)
        CreateDocumentRequest req2 = new CreateDocumentRequest();
        req2.setAuthor("bob");
        req2.setTitle("Doc in DRAFT");
        DocumentResponse doc2 = documentService.createDocument(req2);

        // Batch approve both
        BatchStatusRequest approveReq = new BatchStatusRequest();
        approveReq.setIds(List.of(doc1.getId(), doc2.getId()));
        approveReq.setInitiator("approver");

        List<BatchStatusResult> results = documentService.batchApprove(approveReq);
        assertThat(results).hasSize(2);

        BatchStatusResult ok = results.stream()
                .filter(r -> r.getId().equals(doc1.getId())).findFirst().orElseThrow();
        assertThat(ok.getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        BatchStatusResult conflict = results.stream()
                .filter(r -> r.getId().equals(doc2.getId())).findFirst().orElseThrow();
        assertThat(conflict.getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    // ── Double submit should return CONFLICT ──────────────────────────────────

    @Test
    void doubleSubmit_returnsConflict() {
        CreateDocumentRequest createReq = new CreateDocumentRequest();
        createReq.setAuthor("alice");
        createReq.setTitle("Test");
        DocumentResponse doc = documentService.createDocument(createReq);

        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(doc.getId()));
        req.setInitiator("bob");

        documentService.batchSubmit(req);
        List<BatchStatusResult> second = documentService.batchSubmit(req);

        assertThat(second.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    // ── Approve without submit should return CONFLICT ─────────────────────────

    @Test
    void approve_withoutSubmit_returnsConflict() {
        CreateDocumentRequest createReq = new CreateDocumentRequest();
        createReq.setAuthor("alice");
        createReq.setTitle("Test");
        DocumentResponse doc = documentService.createDocument(createReq);

        BatchStatusRequest approveReq = new BatchStatusRequest();
        approveReq.setIds(List.of(doc.getId()));
        approveReq.setInitiator("approver");

        List<BatchStatusResult> results = documentService.batchApprove(approveReq);
        assertThat(results.get(0).getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    // ── Search by status ──────────────────────────────────────────────────────

    @Test
    void search_byStatus() {
        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setAuthor("alice");
        req.setTitle("Searchable");
        documentService.createDocument(req);

        DocumentSearchRequest searchReq = new DocumentSearchRequest();
        searchReq.setStatus(DocumentStatus.DRAFT);

        var results = documentService.search(searchReq,
                org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(results.getContent()).isNotEmpty();
        assertThat(results.getContent()).allMatch(d -> d.getStatus() == DocumentStatus.DRAFT);
    }
}
