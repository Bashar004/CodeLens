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
@ConfigurationProperties(prefix = "jina")
public class JinaProperties {

    private String apiKey;
    private String apiUrl;
    private String embeddingModel;

    @PostConstruct
    public void validate() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("""
                    ❌ JINA_API_KEY is not set!
                    Fix: Add JINA_API_KEY to your .env file.
                    """);
        }

        String masked = apiKey.substring(0,
                Math.min(8, apiKey.length())) + "***************";

        log.info("✅ Jina AI configured");
        log.info("   Key  : {}", masked);
        log.info("   Model: {}", embeddingModel);
    }
}