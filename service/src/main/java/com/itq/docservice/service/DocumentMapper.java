package com.itq.docservice.service;

import com.itq.docservice.dto.DocumentResponse;
import com.itq.docservice.dto.HistoryEntryResponse;
import com.itq.docservice.entity.Document;
import com.itq.docservice.entity.DocumentHistory;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.List;

@Component
public class DocumentMapper {

    public DocumentResponse toResponse(Document doc, boolean includeHistory) {
        DocumentResponse resp = new DocumentResponse();
        resp.setId(doc.getId());
        resp.setNumber(doc.getNumber());
        resp.setAuthor(doc.getAuthor());
        resp.setTitle(doc.getTitle());
        resp.setStatus(doc.getStatus());
        resp.setCreatedAt(doc.getCreatedAt());
        resp.setUpdatedAt(doc.getUpdatedAt());

        if (includeHistory) {
            resp.setHistory(doc.getHistory().stream().map(this::toHistoryResponse).toList());
        } else {
            resp.setHistory(Collections.emptyList());
        }
        return resp;
    }

    private HistoryEntryResponse toHistoryResponse(DocumentHistory h) {
        HistoryEntryResponse r = new HistoryEntryResponse();
        r.setId(h.getId());
        r.setPerformedBy(h.getPerformedBy());
        r.setAction(h.getAction());
        r.setPerformedAt(h.getPerformedAt());
        r.setComment(h.getComment());
        return r;
    }
}
