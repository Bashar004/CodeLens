package com.codelens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiagramResponse {

    private Long projectId;
    private String projectName;
    private String diagram;
    private int classCount;
    private int relationshipCount;
    private String message;

    // 🆕 V2 fields
    private String packageFilter;
    private List<String> availablePackages;
}