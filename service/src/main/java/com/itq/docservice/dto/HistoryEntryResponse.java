package com.itq.docservice.dto;

import com.itq.docservice.entity.DocumentAction;
import lombok.Data;

import java.time.OffsetDateTime;

@Data
public class HistoryEntryResponse {
    private Long id;
    private String performedBy;
    private DocumentAction action;
    private OffsetDateTime performedAt;
    private String comment;
}
