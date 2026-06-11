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
public class QAResponse {


    private String answer;


    private String question;


    private List<SourceReference> sources;


    private int contextUnitsUsed;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SourceReference {
        private String className;
        private String packageName;
        private String filePath;
        private int    similarityPercent;
    }
}