package com.itq.docservice.dto;

import com.itq.docservice.entity.DocumentStatus;
import lombok.Data;

@Data
public class ConcurrentApprovalResult {
    private int totalAttempts;
    private int successCount;
    private int conflictCount;
    private int errorCount;
    private DocumentStatus finalStatus;
}
