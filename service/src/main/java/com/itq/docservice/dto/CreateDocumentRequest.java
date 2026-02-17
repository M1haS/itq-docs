package com.itq.docservice.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateDocumentRequest {

    @NotBlank(message = "Author must not be blank")
    private String author;

    @NotBlank(message = "Title must not be blank")
    private String title;
}
