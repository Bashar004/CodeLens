package com.codelens.service;

import com.codelens.config.GeminiProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class GeminiService {

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");

    // ✅ KEEP — used for summary (Step 9)
    private static final int MAX_PROMPT_CHARS = 3000;

    // ✅ ADD — used for Q&A (Step 8) — needs more context
    private static final int MAX_QA_PROMPT_CHARS = 8000;

    private final GeminiProperties props;
    private final OkHttpClient     httpClient;
    private final ObjectMapper     objectMapper;

    public GeminiService(GeminiProperties props) {
        this.props        = props;
        this.objectMapper = new ObjectMapper();
        this.httpClient   = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120,   TimeUnit.SECONDS)
                .writeTimeout(30,   TimeUnit.SECONDS)
                .build();
    }

    // ── Generate Text Summary (Step 9) ────────────────────
    // ✅ KEEP — will be re-enabled in Step 9
    public String generateSummary(String prompt) {
        String url = props.getApiUrl()
                + "/" + props.getModel().getText()
                + ":generateContent"
                + "?key=" + props.getApiKey();

        return executeWithRetry(url,
                buildSummaryRequestBody(
                        truncate(prompt, MAX_PROMPT_CHARS)),
                "summary",
                this::parseTextResponse);
    }

    // ── Generate Answer to Code Question (Step 8) ─────────
    // ✅ ADD — new method for Q&A
    public String generateAnswer(String prompt) {
        String url = props.getApiUrl()
                + "/" + props.getModel().getText()
                + ":generateContent"
                + "?key=" + props.getApiKey();

        return executeWithRetry(url,
                buildQARequestBody(
                        truncate(prompt, MAX_QA_PROMPT_CHARS)),
                "answer",
                this::parseTextResponse);
    }

    // ── Execute with Retry + Exponential Backoff ───────────
    // ✅ KEEP — shared by both summary and Q&A
    private <T> T executeWithRetry(String url,
                                   String body,
                                   String operationName,
                                   ResponseParser<T> parser) {

        int maxRetries = props.getRateLimit().getMaxRetries();

        for (int attempt = 1; attempt <= maxRetries; attempt++) {
            try {
                Request request = new Request.Builder()
                        .url(url)
                        .post(RequestBody.create(body, JSON_TYPE))
                        .build();

                try (Response response = httpClient
                        .newCall(request).execute()) {

                    if (response.code() == 429) {
                        long waitMs = props.getRateLimit()
                                .getDelayMs() * attempt;
                        log.warn("⚠️ Rate limited ({}/{}) — {}ms",
                                attempt, maxRetries, waitMs);
                        Thread.sleep(waitMs);
                        continue;
                    }

                    if (!response.isSuccessful()) {
                        String errorBody = response.body() != null
                                ? response.body().string()
                                : "no body";
                        log.warn("⚠️ Gemini {} error: HTTP {} — {}",
                                operationName,
                                response.code(), errorBody);
                        return null;
                    }

                    return parser.parse(response.body().string());
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (IOException e) {
                log.error("❌ Gemini IO ({}/{}): {}",
                        attempt, maxRetries, e.getMessage());
                if (attempt == maxRetries) return null;
            }
        }
        return null;
    }

    // ── Build Summary Request Body (Step 9) ───────────────
    // ✅ RENAMED from buildTextRequestBody → clearer name
    private String buildSummaryRequestBody(String prompt) {
        return """
                {
                    "contents": [{
                        "parts": [{"text": "%s"}]
                    }],
                    "generationConfig": {
                        "maxOutputTokens": 500,
                        "temperature": 0.2
                    }
                }
                """.formatted(escapeJson(prompt));
    }

    // ── Build Q&A Request Body (Step 8) ───────────────────
    // ✅ ADD — Q&A needs longer output and slightly more creative
    private String buildQARequestBody(String prompt) {
        return """
                {
                    "contents": [{
                        "parts": [{"text": "%s"}]
                    }],
                    "generationConfig": {
                        "maxOutputTokens": 1000,
                        "temperature": 0.3
                    }
                }
                """.formatted(escapeJson(prompt));
    }

    // ── Parse Text Response ────────────────────────────────
    // ✅ RENAMED from parseSummaryResponse → used by both methods
    private String parseTextResponse(String responseBody) {
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            return root
                    .path("candidates").get(0)
                    .path("content")
                    .path("parts").get(0)
                    .path("text")
                    .asText(null);
        } catch (Exception e) {
            log.error("❌ Parse Gemini response failed: {}",
                    e.getMessage());
            return null;
        }
    }

    @FunctionalInterface
    private interface ResponseParser<T> {
        T parse(String body) throws IOException;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars
                ? text.substring(0, maxChars) : text;
    }

    private String escapeJson(String text) {
        if (text == null) return "";
        return text
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}