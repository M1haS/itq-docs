package com.itq.docservice.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ConcurrentApprovalRequest {

    @Min(1)
    @Max(50)
    private int threads = 5;

    @Min(1)
    @Max(100)
    private int attempts = 10;

    @NotBlank
    private String initiator;
}
