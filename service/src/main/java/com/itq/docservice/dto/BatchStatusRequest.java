package com.itq.docservice.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class BatchStatusRequest {

    @NotEmpty(message = "ids must not be empty")
    @Size(min = 1, max = 1000, message = "ids list must contain between 1 and 1000 elements")
    private List<Long> ids;

    @NotBlank(message = "initiator must not be blank")
    private String initiator;

    private String comment;
}
