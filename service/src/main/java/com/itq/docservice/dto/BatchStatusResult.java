package com.itq.docservice.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BatchStatusResult {
    private Long id;
    private ResultCode result;
    private String message;

    public enum ResultCode {
        SUCCESS,
        NOT_FOUND,
        CONFLICT,
        REGISTRY_ERROR
    }
}
