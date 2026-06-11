package com.codelens.service;

import org.springframework.stereotype.Component;

@Component
public class LanguageDetector {



    private static final String NON_ENGLISH_PATTERN =
            "[\\u0600-\\u06FF" +  // Arabic
                    "\\u0590-\\u05FF" +   // Hebrew
                    "\\u4E00-\\u9FFF" +   // Chinese
                    "\\u3040-\\u30FF" +   // Japanese
                    "\\u1100-\\u11FF" +   // Korean Jamo
                    "\\uAC00-\\uD7AF" +   // Korean Syllables
                    "\\u0400-\\u04FF" +   // Cyrillic (Russian)
                    "\\u0E00-\\u0E7F" +   // Thai
                    "\\u0370-\\u03FF" +   // Greek
                    "\\u0900-\\u097F" +   // Devanagari (Hindi)
                    "]";

    // ── Check if text contains non-English characters ──────────
    public boolean isEnglish(String text) {
        if (text == null || text.isBlank()) return false;

        return !text.matches(".*" + NON_ENGLISH_PATTERN + ".*");
    }

    // ── Detect which language family it might be ───────────────
    public String detectLanguageHint(String text) {
        if (text.matches(".*[\\u0600-\\u06FF].*")) return "Arabic";
        if (text.matches(".*[\\u0590-\\u05FF].*")) return "Hebrew";
        if (text.matches(".*[\\u4E00-\\u9FFF].*")) return "Chinese";
        if (text.matches(".*[\\u3040-\\u30FF].*")) return "Japanese";
        if (text.matches(".*[\\uAC00-\\uD7AF].*")) return "Korean";
        if (text.matches(".*[\\u0400-\\u04FF].*")) return "Russian";
        if (text.matches(".*[\\u0E00-\\u0E7F].*")) return "Thai";
        if (text.matches(".*[\\u0370-\\u03FF].*")) return "Greek";
        if (text.matches(".*[\\u0900-\\u097F].*")) return "Hindi";
        return "Unknown";
    }
}