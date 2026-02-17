package com.itq.docservice.service;

import com.itq.docservice.dto.BatchStatusResult;
import com.itq.docservice.dto.ConcurrentApprovalRequest;
import com.itq.docservice.dto.ConcurrentApprovalResult;
import com.itq.docservice.entity.Document;
import com.itq.docservice.exception.DocumentNotFoundException;
import com.itq.docservice.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConcurrentApprovalService {

    private final DocumentTransactionService txService;
    private final DocumentRepository documentRepository;

    public ConcurrentApprovalResult test(Long documentId, ConcurrentApprovalRequest req) {
        documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        int totalAttempts = req.getThreads() * req.getAttempts();
        AtomicInteger success = new AtomicInteger();
        AtomicInteger conflict = new AtomicInteger();
        AtomicInteger errors = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(req.getThreads());
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<BatchStatusResult>> futures = new ArrayList<>();

        for (int i = 0; i < totalAttempts; i++) {
            final int attempt = i;
            futures.add(executor.submit(() -> {
                latch.await();
                return txService.approveOne(documentId,
                        req.getInitiator() + "-" + attempt,
                        "concurrent test attempt " + attempt);
            }));
        }

        latch.countDown();

        for (Future<BatchStatusResult> f : futures) {
            try {
                BatchStatusResult res = f.get(15, TimeUnit.SECONDS);
                switch (res.getResult()) {
                    case SUCCESS       -> success.incrementAndGet();
                    case REGISTRY_ERROR -> errors.incrementAndGet();
                    default            -> conflict.incrementAndGet();
                }
            } catch (Exception e) {
                conflict.incrementAndGet();
                log.warn("Concurrent attempt exception: {}", e.getMessage());
            }
        }

        executor.shutdown();

        Document finalDoc = documentRepository.findById(documentId)
                .orElseThrow(() -> new DocumentNotFoundException(documentId));

        ConcurrentApprovalResult result = new ConcurrentApprovalResult();
        result.setTotalAttempts(totalAttempts);
        result.setSuccessCount(success.get());
        result.setConflictCount(conflict.get());
        result.setErrorCount(errors.get());
        result.setFinalStatus(finalDoc.getStatus());

        log.info("Concurrent test done: total={}, success={}, conflict={}, error={}, finalStatus={}",
                totalAttempts, success.get(), conflict.get(), errors.get(), finalDoc.getStatus());

        return result;
    }
}
