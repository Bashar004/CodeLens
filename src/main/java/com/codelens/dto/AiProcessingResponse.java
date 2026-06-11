package com.codelens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiProcessingResponse {

    private Long   projectId;
    private String projectName;
    private int    totalUnitsProcessed;
    private int    summariesGenerated;
    private int    embeddingsGenerated;
    private int    failedUnits;
    private String status;
    private String message;
}
