package com.itq.docservice.service;

import com.itq.docservice.dto.DocumentResponse;
import com.itq.docservice.entity.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentMapperTest {

    private final DocumentMapper mapper = new DocumentMapper();

    private Document buildDoc() {
        Document doc = new Document();
        doc.setId(42L);
        doc.setNumber("DOC-001");
        doc.setAuthor("ivan");
        doc.setTitle("My Doc");
        doc.setStatus(DocumentStatus.DRAFT);
        doc.setCreatedAt(OffsetDateTime.now());
        doc.setUpdatedAt(OffsetDateTime.now());
        return doc;
    }

    @Test
    void toResponse_withoutHistory_mapsAllFields() {
        Document doc = buildDoc();

        DocumentResponse resp = mapper.toResponse(doc, false);

        assertThat(resp.getId()).isEqualTo(42L);
        assertThat(resp.getNumber()).isEqualTo("DOC-001");
        assertThat(resp.getAuthor()).isEqualTo("ivan");
        assertThat(resp.getTitle()).isEqualTo("My Doc");
        assertThat(resp.getStatus()).isEqualTo(DocumentStatus.DRAFT);
        assertThat(resp.getCreatedAt()).isNotNull();
        assertThat(resp.getUpdatedAt()).isNotNull();
        assertThat(resp.getHistory()).isEmpty();
    }

    @Test
    void toResponse_withHistory_includesHistoryEntries() {
        Document doc = buildDoc();

        DocumentHistory h1 = new DocumentHistory();
        h1.setId(1L);
        h1.setDocument(doc);
        h1.setPerformedBy("bob");
        h1.setAction(DocumentAction.SUBMIT);
        h1.setPerformedAt(OffsetDateTime.now());
        h1.setComment("submitted");

        DocumentHistory h2 = new DocumentHistory();
        h2.setId(2L);
        h2.setDocument(doc);
        h2.setPerformedBy("carol");
        h2.setAction(DocumentAction.APPROVE);
        h2.setPerformedAt(OffsetDateTime.now());
        h2.setComment(null);

        doc.setHistory(List.of(h1, h2));

        DocumentResponse resp = mapper.toResponse(doc, true);

        assertThat(resp.getHistory()).hasSize(2);
        assertThat(resp.getHistory().get(0).getPerformedBy()).isEqualTo("bob");
        assertThat(resp.getHistory().get(0).getAction()).isEqualTo(DocumentAction.SUBMIT);
        assertThat(resp.getHistory().get(0).getComment()).isEqualTo("submitted");
        assertThat(resp.getHistory().get(1).getPerformedBy()).isEqualTo("carol");
        assertThat(resp.getHistory().get(1).getComment()).isNull();
    }

    @Test
    void toResponse_withHistoryFalse_returnsEmptyHistoryList() {
        Document doc = buildDoc();
        DocumentHistory h = new DocumentHistory();
        h.setId(1L);
        h.setDocument(doc);
        h.setPerformedBy("bob");
        h.setAction(DocumentAction.SUBMIT);
        h.setPerformedAt(OffsetDateTime.now());
        doc.setHistory(List.of(h));

        DocumentResponse resp = mapper.toResponse(doc, false);

        assertThat(resp.getHistory()).isEmpty();
    }
}
