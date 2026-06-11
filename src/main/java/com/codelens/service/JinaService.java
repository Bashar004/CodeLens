package com.codelens.service;

import com.codelens.config.JinaProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
public class JinaService {

    private static final MediaType JSON_TYPE =
            MediaType.get("application/json; charset=utf-8");
    private static final int OUTPUT_DIMENSIONS = 768;
    private static final int MAX_CHARS         = 8000;

    private final JinaProperties props;
    private final OkHttpClient   httpClient;
    private final ObjectMapper   objectMapper;

    public JinaService(JinaProperties props) {
        this.props        = props;
        this.objectMapper = new ObjectMapper();
        this.httpClient   = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60,  TimeUnit.SECONDS)
                .build();
    }

    // ── Generate Embedding Vector ──────────────────────────
    public float[] generateEmbedding(String text) {
        try {
            String body = """
                    {
                        "model": "%s",
                        "dimensions": %d,
                        "input": ["%s"]
                    }
                    """.formatted(
                    props.getEmbeddingModel(),
                    OUTPUT_DIMENSIONS,
                    escapeJson(truncate(text, MAX_CHARS)));

            Request request = new Request.Builder()
                    .url(props.getApiUrl())
                    .addHeader("Authorization",
                            "Bearer " + props.getApiKey())
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(body, JSON_TYPE))
                    .build();

            try (Response response = httpClient
                    .newCall(request).execute()) {

                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null
                            ? response.body().string() : "no body";
                    log.warn("⚠️ Jina error: HTTP {} — {}",
                            response.code(), errorBody);
                    return null;
                }

                return parseEmbedding(response.body().string());
            }

        } catch (IOException e) {
            log.error("❌ Jina failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Parse Response ─────────────────────────────────────
    // Format: {"data": [{"embedding": [0.1, 0.2, ...]}]}
    private float[] parseEmbedding(String responseBody) {
        try {
            JsonNode values = objectMapper
                    .readTree(responseBody)
                    .path("data").get(0)
                    .path("embedding");

            if (values.isMissingNode() || values.isEmpty()) {
                log.warn("⚠️ Empty embedding from Jina");
                return null;
            }

            float[] vector = new float[values.size()];
            for (int i = 0; i < values.size(); i++) {
                vector[i] = (float) values.get(i).asDouble();
            }
            return vector;

        } catch (Exception e) {
            log.error("❌ Parse Jina response failed: {}",
                    e.getMessage());
            return null;
        }
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