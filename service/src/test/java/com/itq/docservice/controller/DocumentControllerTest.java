package com.itq.docservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.itq.docservice.dto.*;
import com.itq.docservice.entity.DocumentStatus;
import com.itq.docservice.exception.DocumentNotFoundException;
import com.itq.docservice.exception.GlobalExceptionHandler;
import com.itq.docservice.service.ConcurrentApprovalService;
import com.itq.docservice.service.DocumentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.*;
import org.springframework.data.web.config.EnableSpringDataWebSupport;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(DocumentController.class)
@Import(GlobalExceptionHandler.class)
@EnableSpringDataWebSupport
class DocumentControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @MockBean private DocumentService documentService;
    @MockBean private ConcurrentApprovalService concurrentApprovalService;

    private DocumentResponse buildResponse(Long id, DocumentStatus status) {
        DocumentResponse r = new DocumentResponse();
        r.setId(id);
        r.setNumber("DOC-001");
        r.setAuthor("alice");
        r.setTitle("Test");
        r.setStatus(status);
        r.setCreatedAt(OffsetDateTime.now());
        r.setUpdatedAt(OffsetDateTime.now());
        r.setHistory(Collections.emptyList());
        return r;
    }

    // ── POST /api/documents ───────────────────────────────────────────────────

    @Test
    void createDocument_validRequest_returns201() throws Exception {
        DocumentResponse response = buildResponse(1L, DocumentStatus.DRAFT);
        when(documentService.createDocument(any())).thenReturn(response);

        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"alice","title":"Test Document"}
                                """))
                .andDo(print())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.number").value("DOC-001"));
    }

    @Test
    void createDocument_missingAuthor_returns400() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Test Document"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createDocument_blankTitle_returns400() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"author":"alice","title":""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void createDocument_emptyBody_returns400() throws Exception {
        mockMvc.perform(post("/api/documents")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/documents/{id} ───────────────────────────────────────────────

    @Test
    void getDocument_exists_returns200WithHistory() throws Exception {
        DocumentResponse response = buildResponse(1L, DocumentStatus.APPROVED);
        when(documentService.getDocumentWithHistory(1L)).thenReturn(response);

        mockMvc.perform(get("/api/documents/1"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.history").isArray());
    }

    @Test
    void getDocument_notFound_returns404() throws Exception {
        when(documentService.getDocumentWithHistory(999L))
                .thenThrow(new DocumentNotFoundException(999L));

        mockMvc.perform(get("/api/documents/999"))
                .andDo(print())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    // ── GET /api/documents ────────────────────────────────────────────────────

    @Test
    void getDocuments_paged_returns200() throws Exception {
        DocumentResponse doc = buildResponse(1L, DocumentStatus.DRAFT);
        Page<DocumentResponse> page = new PageImpl<>(List.of(doc));
        when(documentService.getDocumentsPaged(isNull(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/documents?page=0&size=10"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    @Test
    void getDocuments_withIds_returns200() throws Exception {
        DocumentResponse doc = buildResponse(1L, DocumentStatus.DRAFT);
        Page<DocumentResponse> page = new PageImpl<>(List.of(doc));
        when(documentService.getDocumentsPaged(eq(List.of(1L, 2L)), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/documents?ids=1,2"))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(1));
    }

    // ── POST /api/documents/submit ────────────────────────────────────────────

    @Test
    void submit_validRequest_returnsResults() throws Exception {
        List<BatchStatusResult> results = List.of(
                new BatchStatusResult(1L, BatchStatusResult.ResultCode.SUCCESS, "ok"),
                new BatchStatusResult(2L, BatchStatusResult.ResultCode.NOT_FOUND, "nf")
        );
        when(documentService.batchSubmit(any())).thenReturn(results);

        mockMvc.perform(post("/api/documents/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1,2],"initiator":"manager"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("SUCCESS"))
                .andExpect(jsonPath("$[1].result").value("NOT_FOUND"));
    }

    @Test
    void submit_emptyIds_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[],"initiator":"manager"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_tooManyIds_returns400() throws Exception {
        List<Long> ids = java.util.stream.LongStream.rangeClosed(1, 1001).boxed().toList();
        mockMvc.perform(post("/api/documents/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                java.util.Map.of("ids", ids, "initiator", "mgr"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submit_missingInitiator_returns400() throws Exception {
        mockMvc.perform(post("/api/documents/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1]}
                                """))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/documents/approve ───────────────────────────────────────────

    @Test
    void approve_validRequest_returnsResults() throws Exception {
        List<BatchStatusResult> results = List.of(
                new BatchStatusResult(1L, BatchStatusResult.ResultCode.SUCCESS, "ok")
        );
        when(documentService.batchApprove(any())).thenReturn(results);

        mockMvc.perform(post("/api/documents/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1],"initiator":"director","comment":"approved"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("SUCCESS"));
    }

    @Test
    void approve_registryError_returnsRegistryErrorCode() throws Exception {
        List<BatchStatusResult> results = List.of(
                new BatchStatusResult(1L, BatchStatusResult.ResultCode.REGISTRY_ERROR, "failed")
        );
        when(documentService.batchApprove(any())).thenReturn(results);

        mockMvc.perform(post("/api/documents/approve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"ids":[1],"initiator":"director"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].result").value("REGISTRY_ERROR"));
    }

    // ── GET /api/documents/search ─────────────────────────────────────────────

    @Test
    void search_byStatus_returns200() throws Exception {
        DocumentResponse doc = buildResponse(1L, DocumentStatus.DRAFT);
        Page<DocumentResponse> page = new PageImpl<>(List.of(doc));
        when(documentService.search(any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/documents/search?status=DRAFT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].status").value("DRAFT"));
    }

    @Test
    void search_noFilters_returnsAll() throws Exception {
        Page<DocumentResponse> page = new PageImpl<>(List.of(buildResponse(1L, DocumentStatus.APPROVED)));
        when(documentService.search(any(), any(Pageable.class))).thenReturn(page);

        mockMvc.perform(get("/api/documents/search"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(1));
    }

    // ── POST /api/documents/{id}/concurrent-approval-test ────────────────────

    @Test
    void concurrentApprovalTest_validRequest_returnsSummary() throws Exception {
        ConcurrentApprovalResult result = new ConcurrentApprovalResult();
        result.setTotalAttempts(10);
        result.setSuccessCount(1);
        result.setConflictCount(9);
        result.setFinalStatus(DocumentStatus.APPROVED);
        when(concurrentApprovalService.test(eq(1L), any())).thenReturn(result);

        mockMvc.perform(post("/api/documents/1/concurrent-approval-test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"threads":5,"attempts":2,"initiator":"tester"}
                                """))
                .andDo(print())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalAttempts").value(10))
                .andExpect(jsonPath("$.successCount").value(1))
                .andExpect(jsonPath("$.finalStatus").value("APPROVED"));
    }
}
