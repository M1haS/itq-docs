package com.itq.docservice.controller;

import com.itq.docservice.dto.*;
import com.itq.docservice.service.ConcurrentApprovalService;
import com.itq.docservice.service.DocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentService documentService;
    private final ConcurrentApprovalService concurrentApprovalService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DocumentResponse createDocument(@Valid @RequestBody CreateDocumentRequest req) {
        return documentService.createDocument(req);
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(@PathVariable("id") Long id) {
        return documentService.getDocumentWithHistory(id);
    }

    @GetMapping
    public Page<DocumentResponse> getDocuments(
            @RequestParam(name = "ids", required = false) List<Long> ids,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.getDocumentsPaged(ids, pageable);
    }

    @PostMapping("/submit")
    public List<BatchStatusResult> submitDocuments(@Valid @RequestBody BatchStatusRequest req) {
        return documentService.batchSubmit(req);
    }

    @PostMapping("/approve")
    public List<BatchStatusResult> approveDocuments(@Valid @RequestBody BatchStatusRequest req) {
        return documentService.batchApprove(req);
    }

    @GetMapping("/search")
    public Page<DocumentResponse> searchDocuments(
            @ModelAttribute DocumentSearchRequest searchReq,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return documentService.search(searchReq, pageable);
    }

    @PostMapping("/{id}/concurrent-approval-test")
    public ConcurrentApprovalResult testConcurrentApproval(
            @PathVariable("id") Long id,
            @Valid @RequestBody ConcurrentApprovalRequest req) {
        return concurrentApprovalService.test(id, req);
    }
}
