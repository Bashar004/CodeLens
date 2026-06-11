package com.codelens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisStatusResponse {

    private Long projectId;
    private String projectName;
    private String projectStatus;
    private int processedUnits;
    private int failedUnits;
    private int totalUnits;
    private int progressPercent;
    private String analysisStatus;
    private String message;
}