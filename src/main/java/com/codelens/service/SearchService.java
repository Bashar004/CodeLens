package com.codelens.service;

import com.codelens.dto.SearchRequest;
import com.codelens.dto.SearchResult;
import com.codelens.model.Project;
import com.codelens.repository.CodeUnitRepository;
import com.codelens.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private static final int SEARCH_LIMIT = 10;
    private final ProjectRepository  projectRepository;
    private final CodeUnitRepository codeUnitRepository;
    private final JinaService        jinaService;
    private final LanguageDetector   languageDetector;

    // ── Main Search Method ─────────────────────────────────────
    public List<SearchResult> search(Long projectId,
                                     Long userId,
                                     SearchRequest request) {

        // ── 1. Validate language — English only ────────────────
        if (!languageDetector.isEnglish(request.getQuery())) {
            String hint = languageDetector
                    .detectLanguageHint(request.getQuery());
            throw new RuntimeException(
                    "Search query must be in English. " +
                            "Detected language: " + hint + ". " +
                            "Please rephrase your query in English.");
        }

        // ── 2. Load and verify project ─────────────────────────
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        if (!project.getOwner().getId().equals(userId)) {
            throw new RuntimeException(
                    "Access denied: project does not belong to you");
        }

        // ── 3. Verify project is indexed ───────────────────────
        if (!"INDEXED".equals(project.getStatus())) {
            throw new RuntimeException(
                    "Project status is '" + project.getStatus()
                            + "'. Please run /analyze first to index the project.");
        }

        log.info("🔍 Searching in '{}': \"{}\" (limit: {})",
                project.getName(),
                request.getQuery());


        // ── 4. Convert query to embedding vector ───────────────
        float[] queryVector = jinaService.generateEmbedding(
                request.getQuery());

        if (queryVector == null) {
            throw new RuntimeException(
                    "Failed to generate embedding for query. " +
                            "Please try again.");
        }

        // ── 5. Convert float[] to pgvector string format ───────
        // pgvector expects: "[0.1, 0.2, -0.3, ...]"
        String vectorString = toVectorString(queryVector);

        // ── 6. Execute vector similarity search ────────────────
        List<Object[]> rawResults = codeUnitRepository
                .findSimilarCodeUnits(
                        projectId,
                        vectorString,
                        SEARCH_LIMIT);

        log.info("🎯 Found {} results for query: \"{}\"",
                rawResults.size(), request.getQuery());

        // ── 7. Map raw results to SearchResult DTOs ────────────
        return rawResults.stream()
                .map(this::mapToSearchResult)
                .collect(Collectors.toList());
    }

    // ── Convert float[] to pgvector string ────────────────────
    // Input:  [0.1f, 0.2f, -0.3f]
    // Output: "[0.1,0.2,-0.3]"
    private String toVectorString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) sb.append(",");
            sb.append(vector[i]);
        }
        sb.append("]");
        return sb.toString();
    }

    // ── Map Object[] row to SearchResult ──────────────────────
    // Column order matches the SELECT in findSimilarCodeUnits:
    // 0=id, 1=class_name, 2=class_type, 3=package_name,
    // 4=file_path, 5=summary, 6=methods_json,
    // 7=fields_json, 8=raw_source_code, 9=parsed_at,
    // 10=project_id, 11=embedding, 12=similarity
    private SearchResult mapToSearchResult(Object[] row) {

        double similarity = row[12] != null
                ? ((Number) row[12]).doubleValue()
                : 0.0;

        // Round similarity to 4 decimal places
        double roundedSimilarity = Math.round(similarity * 10000.0)
                / 10000.0;

        // Convert to percentage (0-100)
        int percent = (int) Math.round(roundedSimilarity * 100);

        return SearchResult.builder()
                .codeUnitId(((Number) row[0]).longValue())
                .className(   (String) row[1])
                .classType(   (String) row[2])
                .packageName( (String) row[3])
                .filePath(    (String) row[4])
                .summary(     (String) row[5])
                .methodsJson( (String) row[6])
                .rawSourceCode(     (String) row[8])
                .similarityScore(roundedSimilarity)
                .similarityPercent(percent)
                .build();
    }

    // ── Internal search — used by QAService ───────────────
// Allows custom limit without exposing it to API users
    public List<SearchResult> searchInternal(Long projectId,
                                             Long userId,
                                             String query,
                                             int limit) {
        // ✅ No language check here — QAService already checked
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        float[] queryVector = jinaService.generateEmbedding(query);
        if (queryVector == null) return List.of();

        String vectorString = toVectorString(queryVector);

        List<Object[]> rawResults = codeUnitRepository
                .findSimilarCodeUnits(projectId, vectorString, limit);

        return rawResults.stream()
                .map(this::mapToSearchResult)
                .collect(Collectors.toList());
    }
}