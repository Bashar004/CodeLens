package com.codelens.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ParseResponse {

    private Long projectId;
    private String projectName;
    private int totalFilesParsed;
    private int totalMethodsExtracted;
    private int totalFieldsExtracted;
    private String status;
    private String message;

}
