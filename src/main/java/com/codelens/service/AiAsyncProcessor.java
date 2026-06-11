package com.codelens.service;

import com.codelens.model.CodeUnit;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
@RequiredArgsConstructor
public class AiAsyncProcessor {

    private final ProjectRepository projectRepository;
    private final CodeUnitRepository codeUnitRepository;
    private final JinaService jinaService;

    @Async("taskExecutor")
    public void processAsync(Long projectId) {

        log.info("🚀 [ASYNC] Starting embedding for project {} in thread: {}",
                projectId, Thread.currentThread().getName());

        AtomicInteger processed = new AtomicInteger(0);
        AtomicInteger failed = new AtomicInteger(0);
        AtomicInteger embeddings = new AtomicInteger(0);

        try {
            List<CodeUnit> pendingUnits = codeUnitRepository
                    .findByProjectIdAndEmbeddingIsNull(projectId);

            long totalUnits = codeUnitRepository.countByProjectId(projectId);

            log.info("📋 [ASYNC] Found {} pending units out of {} total",
                    pendingUnits.size(), totalUnits);

            for (CodeUnit unit : pendingUnits) {
                try {
                    boolean success = processSingleUnit(unit, embeddings);

                    if (success) {
                        int current = processed.incrementAndGet();
                        projectRepository.updateProgress(
                                projectId, current, failed.get());
                        log.info("✅ [ASYNC] Unit {}/{}: {}",
                                current, totalUnits, unit.getClassName());
                    } else {
                        int currentFailed = failed.incrementAndGet();
                        projectRepository.updateProgress(
                                projectId, processed.get(), currentFailed);
                        log.warn("⚠️ [ASYNC] Failed: {}", unit.getClassName());
                    }

                } catch (Exception e) {
                    int currentFailed = failed.incrementAndGet();
                    projectRepository.updateProgress(
                            projectId, processed.get(), currentFailed);
                    log.error("❌ [ASYNC] Error on unit {}: {}",
                            unit.getClassName(), e.getMessage());
                }
            }

            projectRepository.updateStatus(projectId, "INDEXED");
            log.info("🎉 [ASYNC] Done for project {}. Processed: {}, Failed: {}, Embeddings: {}",
                    projectId, processed.get(), failed.get(), embeddings.get());

        } catch (Exception e) {
            projectRepository.updateStatus(projectId, "PARSED");
            log.error("💥 [ASYNC] Fatal error for project {}: {}",
                    projectId, e.getMessage(), e);
        }
    }

    @Transactional
    public boolean processSingleUnit(CodeUnit unit, AtomicInteger embeddings) {
        try {
            String embeddingText = buildEmbeddingText(unit);
            float[] embedding = jinaService.generateEmbedding(embeddingText);
            unit.setEmbedding(embedding);
            codeUnitRepository.save(unit);
            embeddings.incrementAndGet();
            return true;
        } catch (Exception e) {
            log.error("❌ Failed to embed unit {}: {}",
                    unit.getClassName(), e.getMessage());
            throw new RuntimeException(
                    "Embedding failed: " + e.getMessage(), e);
        }
    }

    private String buildEmbeddingText(CodeUnit unit) {
        StringBuilder sb = new StringBuilder();
        sb.append(unit.getClassType()).append(": ")
                .append(unit.getClassName())
                .append(" in package ").append(unit.getPackageName())
                .append("\n");

        String methodNames = extractMethodNames(unit.getMethodsJson());
        if (!methodNames.isBlank()) {
            sb.append("Methods: ").append(methodNames).append("\n");
        }

        if (unit.getRawSourceCode() != null) {
            sb.append("Source: ")
                    .append(truncate(unit.getRawSourceCode(), 500))
                    .append("\n");
        }

        return truncate(sb.toString(), 8000);
    }

    private String extractMethodNames(String methodsJson) {
        if (methodsJson == null || methodsJson.isBlank()) return "";
        StringBuilder names = new StringBuilder();
        String[] parts = methodsJson.split("\"name\":\"");
        for (int i = 1; i < parts.length; i++) {
            int end = parts[i].indexOf('"');
            if (end > 0) {
                if (names.length() > 0) names.append(", ");
                names.append(parts[i], 0, end);
            }
        }
        return names.toString();
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() <= maxChars ? text : text.substring(0, maxChars);
    }
}