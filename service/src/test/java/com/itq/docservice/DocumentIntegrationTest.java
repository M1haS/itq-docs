package com.itq.docservice;

import com.itq.docservice.dto.*;
import com.itq.docservice.entity.*;
import com.itq.docservice.repository.ApprovalRegistryRepository;
import com.itq.docservice.repository.DocumentHistoryRepository;
import com.itq.docservice.repository.DocumentRepository;
import com.itq.docservice.service.DocumentService;
import com.itq.docservice.service.DocumentTransactionService;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.*;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@SpringBootTest
@Tag("integration")
class DocumentIntegrationTest {

    // Manage container lifecycle manually — avoids @Testcontainers extension
    // running before @BeforeAll and crashing when Docker is absent.
    static PostgreSQLContainer<?> postgres;

    static {
        boolean dockerAvailable;
        try {
            org.testcontainers.DockerClientFactory.instance().client();
            dockerAvailable = true;
        } catch (Throwable e) {
            dockerAvailable = false;
        }
        if (dockerAvailable) {
            postgres = new PostgreSQLContainer<>("postgres:15-alpine")
                    .withDatabaseName("itq_test")
                    .withUsername("itq")
                    .withPassword("itq_pass");
            postgres.start();
        }
    }

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        if (postgres != null && postgres.isRunning()) {
            registry.add("spring.datasource.url", postgres::getJdbcUrl);
            registry.add("spring.datasource.username", postgres::getUsername);
            registry.add("spring.datasource.password", postgres::getPassword);
            registry.add("spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(postgres != null && postgres.isRunning(),
                "Docker unavailable — skipping integration tests");
    }

    @Autowired private DocumentService documentService;
    @Autowired private DocumentTransactionService txService;
    @Autowired private DocumentRepository documentRepository;
    @Autowired private DocumentHistoryRepository historyRepository;
    @Autowired private ApprovalRegistryRepository registryRepository;

    @BeforeEach
    void cleanUp() {
        registryRepository.deleteAll();
        historyRepository.deleteAll();
        documentRepository.deleteAll();
    }

    private DocumentResponse createDoc(String author, String title) {
        CreateDocumentRequest req = new CreateDocumentRequest();
        req.setAuthor(author);
        req.setTitle(title);
        return documentService.createDocument(req);
    }

    private void submit(Long id) {
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(id));
        req.setInitiator("submitter");
        documentService.batchSubmit(req);
    }

    @Test
    void happyPath_createSubmitApprove() {
        DocumentResponse doc = createDoc("alice", "Contract #1");
        assertThat(doc.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(doc.getNumber()).matches("DOC-\\d{8}-\\d+");

        BatchStatusRequest submitReq = new BatchStatusRequest();
        submitReq.setIds(List.of(doc.getId()));
        submitReq.setInitiator("bob");
        submitReq.setComment("send to review");
        assertThat(documentService.batchSubmit(submitReq).get(0).getResult())
                .isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        DocumentResponse afterSubmit = documentService.getDocumentWithHistory(doc.getId());
        assertThat(afterSubmit.getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
        assertThat(afterSubmit.getHistory()).hasSize(1);
        assertThat(afterSubmit.getHistory().get(0).getAction()).isEqualTo(DocumentAction.SUBMIT);
        assertThat(afterSubmit.getHistory().get(0).getPerformedBy()).isEqualTo("bob");
        assertThat(afterSubmit.getHistory().get(0).getComment()).isEqualTo("send to review");

        BatchStatusRequest approveReq = new BatchStatusRequest();
        approveReq.setIds(List.of(doc.getId()));
        approveReq.setInitiator("carol");
        assertThat(documentService.batchApprove(approveReq).get(0).getResult())
                .isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        DocumentResponse afterApprove = documentService.getDocumentWithHistory(doc.getId());
        assertThat(afterApprove.getStatus()).isEqualTo(DocumentStatus.APPROVED);
        assertThat(afterApprove.getHistory()).hasSize(2);

        assertThat(registryRepository.count()).isEqualTo(1);
        assertThat(registryRepository.findAll().get(0).getApprovedBy()).isEqualTo("carol");
    }

    @Test
    void batchSubmit_mixedResults() {
        DocumentResponse existing = createDoc("alice", "Doc");
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(existing.getId(), 99999L));
        req.setInitiator("mgr");
        List<BatchStatusResult> results = documentService.batchSubmit(req);

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> r.getId().equals(existing.getId()))
                .findFirst().orElseThrow().getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        assertThat(results.stream().filter(r -> r.getId().equals(99999L))
                .findFirst().orElseThrow().getResult()).isEqualTo(BatchStatusResult.ResultCode.NOT_FOUND);
    }

    @Test
    void batchApprove_partialResults_draftConflict() {
        DocumentResponse doc1 = createDoc("alice", "For approve");
        submit(doc1.getId());
        DocumentResponse doc2 = createDoc("bob", "Still draft");

        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(doc1.getId(), doc2.getId()));
        req.setInitiator("director");
        List<BatchStatusResult> results = documentService.batchApprove(req);

        assertThat(results).hasSize(2);
        assertThat(results.stream().filter(r -> r.getId().equals(doc1.getId()))
                .findFirst().orElseThrow().getResult()).isEqualTo(BatchStatusResult.ResultCode.SUCCESS);
        assertThat(results.stream().filter(r -> r.getId().equals(doc2.getId()))
                .findFirst().orElseThrow().getResult()).isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
        assertThat(registryRepository.count()).isEqualTo(1);
    }

    @Test
    void approveOne_rollsBack_whenDuplicateRegistryEntry() {
        DocumentResponse doc = createDoc("alice", "Duplicate test");
        submit(doc.getId());

        assertThat(txService.approveOne(doc.getId(), "carol", "first").getResult())
                .isEqualTo(BatchStatusResult.ResultCode.SUCCESS);

        Document raw = documentRepository.findById(doc.getId()).orElseThrow();
        raw.setStatus(DocumentStatus.SUBMITTED);
        documentRepository.save(raw);

        assertThat(txService.approveOne(doc.getId(), "eve", "second").getResult())
                .isEqualTo(BatchStatusResult.ResultCode.REGISTRY_ERROR);

        assertThat(registryRepository.count()).isEqualTo(1);
        assertThat(documentRepository.findById(doc.getId()).orElseThrow().getStatus())
                .isEqualTo(DocumentStatus.SUBMITTED);
    }

    @Test
    void submit_alreadySubmitted_returnsConflict() {
        DocumentResponse doc = createDoc("alice", "Doc");
        submit(doc.getId());
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(doc.getId()));
        req.setInitiator("bob");
        assertThat(documentService.batchSubmit(req).get(0).getResult())
                .isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    @Test
    void approve_draftDoc_returnsConflict() {
        DocumentResponse doc = createDoc("alice", "Doc");
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(doc.getId()));
        req.setInitiator("carol");
        assertThat(documentService.batchApprove(req).get(0).getResult())
                .isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    @Test
    void approve_approvedDoc_returnsConflict() {
        DocumentResponse doc = createDoc("alice", "Doc");
        submit(doc.getId());
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(List.of(doc.getId()));
        req.setInitiator("carol");
        documentService.batchApprove(req);
        assertThat(documentService.batchApprove(req).get(0).getResult())
                .isEqualTo(BatchStatusResult.ResultCode.CONFLICT);
    }

    @Test
    void search_byStatus() {
        DocumentResponse d1 = createDoc("alice", "Draft Doc");
        DocumentResponse d2 = createDoc("bob", "Also Draft");
        submit(d2.getId());

        DocumentSearchRequest req = new DocumentSearchRequest();
        req.setStatus(DocumentStatus.DRAFT);
        Page<DocumentResponse> page = documentService.search(req, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getId()).isEqualTo(d1.getId());
    }

    @Test
    void search_byAuthor_caseInsensitive() {
        createDoc("Alice", "Doc 1");
        createDoc("BOB", "Doc 2");
        DocumentSearchRequest req = new DocumentSearchRequest();
        req.setAuthor("alice");
        Page<DocumentResponse> page = documentService.search(req, PageRequest.of(0, 10));
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getAuthor()).isEqualTo("Alice");
    }

    @Test
    void search_byDateRange_inRange() {
        DocumentResponse doc = createDoc("alice", "In range");
        DocumentSearchRequest req = new DocumentSearchRequest();
        req.setFrom(OffsetDateTime.now().minusHours(1));
        req.setTo(OffsetDateTime.now().plusHours(1));
        Page<DocumentResponse> page = documentService.search(req, PageRequest.of(0, 10));
        assertThat(page.getContent().stream().anyMatch(d -> d.getId().equals(doc.getId()))).isTrue();
    }

    @Test
    void search_byDateRange_excludesFuture() {
        createDoc("alice", "Doc");
        DocumentSearchRequest req = new DocumentSearchRequest();
        req.setFrom(OffsetDateTime.now().plusHours(1));
        req.setTo(OffsetDateTime.now().plusHours(2));
        assertThat(documentService.search(req, PageRequest.of(0, 10)).getContent()).isEmpty();
    }

    @Test
    void getDocumentsPaged_paginationWorks() {
        for (int i = 0; i < 5; i++) createDoc("user" + i, "Doc " + i);
        Page<DocumentResponse> p1 = documentService.getDocumentsPaged(null, PageRequest.of(0, 2, Sort.by("id")));
        Page<DocumentResponse> p2 = documentService.getDocumentsPaged(null, PageRequest.of(1, 2, Sort.by("id")));
        assertThat(p1.getContent()).hasSize(2);
        assertThat(p2.getContent()).hasSize(2);
        assertThat(p1.getTotalElements()).isEqualTo(5);
        assertThat(p1.getContent().get(0).getId()).isNotEqualTo(p2.getContent().get(0).getId());
    }

    @Test
    void getDocumentsByIds_batchFetch() {
        DocumentResponse d1 = createDoc("alice", "Doc1");
        DocumentResponse d2 = createDoc("bob", "Doc2");
        createDoc("carol", "Doc3");
        List<DocumentResponse> results = documentService.getDocumentsByIds(List.of(d1.getId(), d2.getId()));
        assertThat(results).hasSize(2);
        assertThat(results.stream().map(DocumentResponse::getId))
                .containsExactlyInAnyOrder(d1.getId(), d2.getId());
    }

    @Test
    void batchSubmit_100Docs_allSucceed() {
        List<Long> ids = new java.util.ArrayList<>();
        for (int i = 0; i < 100; i++) ids.add(createDoc("u" + i, "D" + i).getId());
        BatchStatusRequest req = new BatchStatusRequest();
        req.setIds(ids);
        req.setInitiator("bulk");
        long success = documentService.batchSubmit(req).stream()
                .filter(r -> r.getResult() == BatchStatusResult.ResultCode.SUCCESS).count();
        assertThat(success).isEqualTo(100);
    }
}
