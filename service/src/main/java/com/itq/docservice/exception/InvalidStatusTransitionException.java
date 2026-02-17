package com.itq.docservice.exception;

import com.itq.docservice.entity.DocumentStatus;

public class InvalidStatusTransitionException extends RuntimeException {
    public InvalidStatusTransitionException(Long id, DocumentStatus current, DocumentStatus target) {
        super(String.format("Document %d: invalid transition %s -> %s", id, current, target));
    }
}
