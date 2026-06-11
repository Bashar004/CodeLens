package com.codelens.dto;


import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProjectResponse {

        private Long id;
        private String name;
        private String status;
        private Long fileSize;
        private Integer totalFiles;
        private LocalDateTime uploadedAt;
        private String message;

}
