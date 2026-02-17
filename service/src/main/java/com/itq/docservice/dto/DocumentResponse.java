package com.itq.docservice.dto;

import com.itq.docservice.entity.DocumentStatus;
import lombok.Data;

import java.time.OffsetDateTime;
import java.util.List;

@Data
public class DocumentResponse {
    private Long id;
    private String number;
    private String author;
    private String title;
    private DocumentStatus status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private List<HistoryEntryResponse> history;
}
