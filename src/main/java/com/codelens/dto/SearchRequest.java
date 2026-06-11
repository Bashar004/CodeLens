package com.codelens.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class SearchRequest {

    @NotBlank(message = "Search query cannot be empty")
    @Size(min = 3, max = 500,
            message = "Query must be between 3 and 500 characters")
    private String query;
}