package com.codelens.service;

import com.codelens.dto.QARequest;
import com.codelens.dto.QAResponse;
import com.codelens.dto.SearchRequest;
import com.codelens.dto.SearchResult;
import com.codelens.model.Project;
import com.codelens.repository.ProjectRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class QAService {

    // Number of code units to use as context for answering
    private static final int CONTEXT_UNITS = 8;

    private final ProjectRepository projectRepository;
    private final SearchService     searchService;
    private final GeminiService     geminiService;
    private final LanguageDetector  languageDetector;

    public QAResponse answer(Long projectId,
                             Long userId,
                             QARequest request) {

        // ── 1. Language check — English only ───────────────
        if (!languageDetector.isEnglish(request.getQuestion())) {
            String hint = languageDetector
                    .detectLanguageHint(request.getQuestion());
            throw new RuntimeException(
                    "Question must be in English. " +
                            "Detected language: " + hint + ". " +
                            "Please rephrase your question in English.");
        }

        // ── 2. Verify project exists and is owned ──────────
        Project project = projectRepository.findById(projectId)
                .orElseThrow(() -> new RuntimeException(
                        "Project not found: " + projectId));

        if (!project.getOwner().getId().equals(userId)) {
            throw new RuntimeException(
                    "Access denied: project does not belong to you");
        }

        if (!"INDEXED".equals(project.getStatus())) {
            throw new RuntimeException(
                    "Project is not indexed yet. " +
                            "Please run /analyze first.");
        }

        log.info("❓ Q&A for '{}': \"{}\"",
                project.getName(), request.getQuestion());

        // ── 3. Find relevant code using vector search ──────
        // Reuse SearchService — no duplication!
        SearchRequest searchRequest = new SearchRequest();
        searchRequest.setQuery(request.getQuestion());

        List<SearchResult> relevantCode = searchService
                .searchInternal(projectId, userId,
                        request.getQuestion(), CONTEXT_UNITS);

        if (relevantCode.isEmpty()) {
            return QAResponse.builder()
                    .question(request.getQuestion())
                    .answer("No relevant code found in this project " +
                            "to answer your question.")
                    .sources(List.of())
                    .contextUnitsUsed(0)
                    .build();
        }

        log.info("📚 Using {} code units as context",
                relevantCode.size());

        // ── 4. Build prompt with code context ──────────────
        String prompt = buildQAPrompt(
                request.getQuestion(),
                relevantCode,
                project.getName());

        // ── 5. Ask Gemini ──────────────────────────────────
        String answer = geminiService.generateAnswer(prompt);

        if (answer == null) {
            throw new RuntimeException(
                    "Failed to generate answer. " +
                            "Please try again in a moment.");
        }

        log.info("✅ Answer generated successfully");

        // ── 6. Build source references ─────────────────────
        List<QAResponse.SourceReference> sources = relevantCode
                .stream()
                .map(r -> QAResponse.SourceReference.builder()
                        .className(r.getClassName())
                        .packageName(r.getPackageName())
                        .filePath(r.getFilePath())
                        .similarityPercent(r.getSimilarityPercent())
                        .build())
                .collect(Collectors.toList());

        // ── 7. Return complete response ────────────────────
        return QAResponse.builder()
                .question(request.getQuestion())
                .answer(answer.trim())
                .sources(sources)
                .contextUnitsUsed(relevantCode.size())
                .build();
    }

    // ── Build Smart Q&A Prompt ─────────────────────────────
    private String buildQAPrompt(String question,
                                 List<SearchResult> codeUnits,
                                 String projectName) {
        StringBuilder sb = new StringBuilder();

        sb.append("""
                You are an expert Java code analyst for the project "%s".
                Answer the developer's question based ONLY on the provided code.
                Be technical, precise, and reference specific class names and methods.
                If the answer cannot be found in the provided code, say so clearly.
                Do not make assumptions beyond what the code shows.
                Write in plain text — no markdown formatting.
                
                QUESTION: %s
                
                RELEVANT CODE FROM THE PROJECT:
                """.formatted(projectName, question));

        // Add each relevant code unit as context
        for (int i = 0; i < codeUnits.size(); i++) {
            SearchResult unit = codeUnits.get(i);
            sb.append("\n--- Source %d: %s (%d%% relevant) ---\n"
                    .formatted(i + 1,
                            unit.getClassName(),
                            unit.getSimilarityPercent()));
            sb.append("Package: ").append(unit.getPackageName())
                    .append("\n");

            // Add summary if available
            if (unit.getSummary() != null
                    && !unit.getSummary().isBlank()) {
                sb.append("Summary: ").append(unit.getSummary())
                        .append("\n");
            }

            // Add methods
            if (unit.getRawSourceCode() != null) {
                String code = unit.getRawSourceCode();
                String truncatedCode = code.length() > 800
                        ? code.substring(0, 800) + "\n... (truncated)"
                        : code;
                sb.append("Source Code:\n")
                        .append(truncatedCode).append("\n");
            }

            sb.append("\n");
        }

        sb.append("\nANSWER (based only on the code above):");

        return sb.toString();
    }

    // ── Extract method names from JSON ─────────────────────
    private String extractMethodNames(String methodsJson) {
        StringBuilder names = new StringBuilder();
        String target = "\"name\":\"";
        int idx = 0;
        while ((idx = methodsJson.indexOf(target, idx)) != -1) {
            int start = idx + target.length();
            int end   = methodsJson.indexOf("\"", start);
            if (end > start) {
                if (names.length() > 0) names.append(", ");
                names.append(methodsJson, start, end);
            }
            idx = end + 1;
        }
        return names.toString();
    }
}