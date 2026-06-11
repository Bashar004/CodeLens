package com.codelens.controller;

import com.codelens.dto.AiProcessingResponse;
import com.codelens.dto.AnalysisStatusResponse;
import com.codelens.model.Project;
import com.codelens.model.User;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import com.codelens.service.AiSummaryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/projects")
@RequiredArgsConstructor
public class AiController {

    private final AiSummaryService aiSummaryService;
    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;

    @PostMapping("/{id}/analyze")
    public ResponseEntity<AiProcessingResponse> analyzeProject(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        log.info("🎯 User {} triggered analysis for project {}",
                currentUser.getEmail(), id);

        AiProcessingResponse response =
                aiSummaryService.processProject(id, currentUser.getId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    @GetMapping("/{id}/analyze/status")
    public ResponseEntity<AnalysisStatusResponse> getAnalysisStatus(
            @PathVariable Long id,
            @AuthenticationPrincipal User currentUser) {

        // Security check — verify ownership
        Project project = projectRepository.findByIdAndOwnerId(id, currentUser.getId())
                .orElseThrow(() -> new RuntimeException(
                        "Project not found or access denied"));

        long totalUnits = codeUnitRepository.countByProjectId(id);

        // ✅ Read progress directly from DB — bypasses Hibernate cache
        Integer processedUnits = projectRepository.findProcessedUnits(id);
        Integer failedUnits = projectRepository.findFailedUnits(id);
        String currentStatus = projectRepository.findStatusById(id);

        // Null safety
        int processed = processedUnits != null ? processedUnits : 0;
        int failed = failedUnits != null ? failedUnits : 0;
        String status = currentStatus != null ? currentStatus : project.getStatus();

        int progressPercent = totalUnits > 0
                ? (int) Math.min(100, (processed * 100.0 / totalUnits))
                : 0;

        String analysisStatus = switch (status) {
            case "INDEXING" -> "IN_PROGRESS";
            case "INDEXED"  -> "COMPLETED";
            case "PARSED"   -> processed > 0 ? "PARTIALLY_COMPLETE" : "NOT_STARTED";
            default         -> "UNKNOWN";
        };

        String message = switch (analysisStatus) {
            case "IN_PROGRESS" -> String.format(
                    "Processing... %d of %d units complete (%d%%)",
                    processed, totalUnits, progressPercent);
            case "COMPLETED" -> String.format(
                    "Embedding complete: %d succeeded, %d failed out of %d total",
                    processed, failed, totalUnits);
            case "NOT_STARTED" ->
                    "Embedding not started. Call POST /analyze to begin.";
            case "PARTIALLY_COMPLETE" -> String.format(
                    "Previous run partial: %d done. Call POST /analyze to resume.",
                    processed);
            default -> "Unknown status";
        };

        AnalysisStatusResponse statusResponse = AnalysisStatusResponse.builder()
                .projectId(id)
                .projectName(project.getName())
                .projectStatus(status)
                .processedUnits(processed)
                .failedUnits(failed)
                .totalUnits((int) totalUnits)
                .progressPercent(progressPercent)
                .analysisStatus(analysisStatus)
                .message(message)
                .build();

        return ResponseEntity.ok(statusResponse);
    }
}