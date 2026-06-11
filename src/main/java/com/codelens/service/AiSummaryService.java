package com.codelens.service;

import com.codelens.dto.AiProcessingResponse;
import com.codelens.model.Project;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiSummaryService {

    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;
    private final AiAsyncProcessor aiAsyncProcessor;

    @Transactional
    public AiProcessingResponse processProject(Long projectId, Long userId) {

        Project project = projectRepository.findByIdAndOwnerId(projectId, userId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found or access denied"));

        if (!"PARSED".equals(project.getStatus())
                && !"INDEXED".equals(project.getStatus())) {
            throw new RuntimeException(
                    "Project must be in PARSED or INDEXED status. " +
                            "Current status: " + project.getStatus());
        }

        long pendingCount = codeUnitRepository
                .countByProjectIdAndEmbeddingIsNull(projectId);

        if (pendingCount == 0) {
            return AiProcessingResponse.builder()
                    .projectId(projectId)
                    .projectName(project.getName())
                    .totalUnitsProcessed(0)
                    .summariesGenerated(0)
                    .embeddingsGenerated(0)
                    .failedUnits(0)
                    .status("ALREADY_COMPLETE")
                    .message("All code units already have embeddings.")
                    .build();
        }

        projectRepository.updateStatus(projectId, "INDEXING");
        projectRepository.updateProgress(projectId, 0, 0);

        // ✅ Called on a SEPARATE bean — @Async proxy works correctly now
        aiAsyncProcessor.processAsync(projectId);

        long totalUnits = codeUnitRepository.countByProjectId(projectId);

        return AiProcessingResponse.builder()
                .projectId(projectId)
                .projectName(project.getName())
                .totalUnitsProcessed((int) totalUnits)
                .summariesGenerated(0)
                .embeddingsGenerated(0)
                .failedUnits(0)
                .status("STARTED")
                .message("Embedding started in background. " + pendingCount +
                        " units to process. Poll /analyze/status for progress.")
                .build();
    }
}