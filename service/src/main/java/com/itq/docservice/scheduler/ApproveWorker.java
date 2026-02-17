package com.itq.docservice.scheduler;

import com.itq.docservice.dto.BatchStatusResult;
import com.itq.docservice.entity.DocumentStatus;
import com.itq.docservice.repository.DocumentRepository;
import com.itq.docservice.service.DocumentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.workers.approve.enabled", havingValue = "true", matchIfMissing = true)
public class ApproveWorker {

    private final DocumentRepository documentRepository;
    private final DocumentService documentService;

    @Value("${app.batch-size:50}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${app.workers.approve.fixed-delay-ms:15000}")
    public void process() {
        Page<Long> page = documentRepository.findIdsByStatus(
                DocumentStatus.SUBMITTED, PageRequest.of(0, batchSize));

        if (page.isEmpty()) {
            log.debug("[APPROVE-worker] No SUBMITTED documents to process");
            return;
        }

        List<Long> ids = page.getContent();
        long total = page.getTotalElements();
        log.info("[APPROVE-worker] Processing batch: {} documents (total SUBMITTED: {})", ids.size(), total);
        long start = System.currentTimeMillis();

        List<BatchStatusResult> results = documentService.batchApprove(buildRequest(ids));

        long success = results.stream().filter(r -> r.getResult() == BatchStatusResult.ResultCode.SUCCESS).count();
        long failed = results.size() - success;
        log.info("[APPROVE-worker] Batch done in {}ms: success={}, failed={}, remaining≈{}",
                System.currentTimeMillis() - start, success, failed, Math.max(0, total - ids.size()));
    }

    private com.itq.docservice.dto.BatchStatusRequest buildRequest(List<Long> ids) {
        var req = new com.itq.docservice.dto.BatchStatusRequest();
        req.setIds(ids);
        req.setInitiator("approve-worker");
        req.setComment("Auto-approved by background worker");
        return req;
    }
}
