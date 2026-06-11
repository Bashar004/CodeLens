package com.codelens.config;

import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Slf4j
@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {

    private String apiKey;
    private String apiUrl;

    // ✅ Only text model — embedding moved to Jina
    private Model model = new Model();
    private RateLimit rateLimit = new RateLimit();

    @Getter
    @Setter
    public static class Model {
        private String text; // ← only text, no embedding
    }

    @Getter
    @Setter
    public static class RateLimit {
        private long delayMs;
        private int maxRetries;
    }

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("""
                    ❌ GEMINI_API_KEY is not set!
                    Fix: Add GEMINI_API_KEY to your .env file.
                    """);
        }

        String masked = apiKey.substring(0,
                Math.min(8, apiKey.length())) + "***************";

        log.info("✅ Gemini AI configured");
        log.info("   Key  : {}", masked);
        log.info("   Text : {}", model.getText());
        log.info("   Delay: {}ms", rateLimit.getDelayMs());
        log.info("   Retry: {} max", rateLimit.getMaxRetries());
    }
}