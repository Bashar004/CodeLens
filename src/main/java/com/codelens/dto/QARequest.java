package com.codelens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class QARequest {

    @NotBlank(message = "Question cannot be empty")
    @Size(min = 10, max = 1000,
            message = "Question must be between 10 and 1000 characters")
    private String question;
}