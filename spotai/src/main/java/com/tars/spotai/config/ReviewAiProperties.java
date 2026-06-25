package com.tars.spotai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "spotai.ai.review-summary")
public class ReviewAiProperties {
    private boolean enabled;
    private int topK = 20;
    private double similarityThreshold = 0.2;
    private int minReviewCount = 3;
    private long cacheTtlMinutes = 60;
    private int maxIndexReviews = 500;
    private String embeddingModel = "text-embedding-v3";
    private String indexName = "review_vector_idx";
    private String keyPrefix = "review:vector:";
    private String adminKey = "";
    private final VectorRedis vectorRedis = new VectorRedis();

    @Data
    public static class VectorRedis {
        private String host = "localhost";
        private int port = 6380;
        private String password = "";
        private int timeoutMillis = 3000;
    }
}
