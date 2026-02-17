package com.itq.docservice.repository;

import com.itq.docservice.entity.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

@DataJpaTest
@Tag("integration")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(LiquibaseAutoConfiguration.class)
class DocumentRepositoryTest {

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
                    .withDatabaseName("itq_repo_test")
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
            registry.add("spring.liquibase.change-log", () -> "classpath:db/changelog/master.yaml");
        }
    }

    @BeforeAll
    static void requireDocker() {
        assumeTrue(postgres != null && postgres.isRunning(),
                "Docker unavailable — skipping repository tests");
    }

    @Autowired
    private DocumentRepository documentRepository;

    private Document persist(String number, DocumentStatus status) {
        Document doc = new Document();
        doc.setNumber(number);
        doc.setAuthor("alice");
        doc.setTitle("Title " + number);
        doc.setStatus(status);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        return documentRepository.save(doc);
    }

    @Test
    void save_andFindById_works() {
        Document saved = persist("DOC-001", DocumentStatus.DRAFT);
        Optional<Document> found = documentRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getNumber()).isEqualTo("DOC-001");
        assertThat(found.get().getStatus()).isEqualTo(DocumentStatus.DRAFT);
    }

    @Test
    void findIdsByStatus_returnsOnlyMatchingStatus() {
        persist("DOC-001", DocumentStatus.DRAFT);
        persist("DOC-002", DocumentStatus.DRAFT);
        persist("DOC-003", DocumentStatus.SUBMITTED);
        var draftIds = documentRepository.findIdsByStatus(
                DocumentStatus.DRAFT, org.springframework.data.domain.PageRequest.of(0, 10));
        assertThat(draftIds.getContent()).hasSize(2);
        assertThat(draftIds.getTotalElements()).isEqualTo(2);
    }

    @Test
    void findIdsByStatus_pagination_works() {
        for (int i = 0; i < 10; i++) persist("DOC-" + i, DocumentStatus.DRAFT);
        var page = documentRepository.findIdsByStatus(
                DocumentStatus.DRAFT, org.springframework.data.domain.PageRequest.of(0, 3));
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getTotalElements()).isEqualTo(10);
        assertThat(page.getTotalPages()).isEqualTo(4);
    }

    @Test
    void findAllByIdIn_returnsOnlyRequestedIds() {
        Document d1 = persist("DOC-001", DocumentStatus.DRAFT);
        Document d2 = persist("DOC-002", DocumentStatus.SUBMITTED);
        persist("DOC-003", DocumentStatus.APPROVED);
        List<Document> result = documentRepository.findAllByIdIn(List.of(d1.getId(), d2.getId()));
        assertThat(result).hasSize(2);
        assertThat(result.stream().map(Document::getId))
                .containsExactlyInAnyOrder(d1.getId(), d2.getId());
    }

    @Test
    void findAll_withStatusSpec_filtersByStatus() {
        persist("DOC-001", DocumentStatus.DRAFT);
        persist("DOC-002", DocumentStatus.SUBMITTED);
        persist("DOC-003", DocumentStatus.APPROVED);
        List<Document> result = documentRepository.findAll(DocumentSpecification.hasStatus(DocumentStatus.SUBMITTED));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getStatus()).isEqualTo(DocumentStatus.SUBMITTED);
    }

    @Test
    void findAll_withAuthorSpec_caseInsensitive() {
        Document a = new Document();
        a.setNumber("DOC-A"); a.setAuthor("Alice"); a.setTitle("T");
        a.setStatus(DocumentStatus.DRAFT);
        a.setCreatedAt(OffsetDateTime.now()); a.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(a);
        Document b = new Document();
        b.setNumber("DOC-B"); b.setAuthor("BOB"); b.setTitle("T");
        b.setStatus(DocumentStatus.DRAFT);
        b.setCreatedAt(OffsetDateTime.now()); b.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(b);

        List<Document> result = documentRepository.findAll(DocumentSpecification.hasAuthor("alice"));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getAuthor()).isEqualTo("Alice");
    }

    @Test
    void findAll_withDateRangeSpec_filtersCorrectly() {
        Document old = new Document();
        old.setNumber("DOC-OLD"); old.setAuthor("alice"); old.setTitle("Old");
        old.setStatus(DocumentStatus.DRAFT);
        old.setCreatedAt(OffsetDateTime.now().minusDays(10));
        old.setUpdatedAt(OffsetDateTime.now().minusDays(10));
        documentRepository.save(old);

        Document recent = new Document();
        recent.setNumber("DOC-RECENT"); recent.setAuthor("alice"); recent.setTitle("Recent");
        recent.setStatus(DocumentStatus.DRAFT);
        recent.setCreatedAt(OffsetDateTime.now());
        recent.setUpdatedAt(OffsetDateTime.now());
        documentRepository.save(recent);

        List<Document> result = documentRepository.findAll(
                DocumentSpecification.createdAfter(OffsetDateTime.now().minusHours(1)));
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getNumber()).isEqualTo("DOC-RECENT");
    }

    @Test
    void findAll_sortByIdAsc_returnsInOrder() {
        persist("DOC-FIRST", DocumentStatus.DRAFT);
        persist("DOC-SECOND", DocumentStatus.DRAFT);
        List<Document> results = documentRepository.findAll(Sort.by(Sort.Direction.ASC, "id"));
        assertThat(results.get(0).getId()).isLessThan(results.get(1).getId());
    }

    @Test
    void findAll_nullStatusSpec_returnsAll() {
        persist("DOC-001", DocumentStatus.DRAFT);
        persist("DOC-002", DocumentStatus.SUBMITTED);
        List<Document> result = documentRepository.findAll(DocumentSpecification.hasStatus(null));
        assertThat(result).hasSize(2);
    }
}
