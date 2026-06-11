package com.codelens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private Long   codeUnitId;
    private String className;
    private String classType;
    private String packageName;
    private String filePath;
    private String summary;
    private String methodsJson;
    private String rawSourceCode;

    // Similarity score: 1.0 = perfect match, 0.0 = no match
    private double similarityScore;

    // Percentage for easier reading in UI
    private int similarityPercent;
}