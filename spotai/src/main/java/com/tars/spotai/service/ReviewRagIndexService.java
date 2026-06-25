package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.ReviewEmbedding;
import com.tars.spotai.repository.ReviewEmbeddingRepository;
import com.tars.spotai.repository.ReviewRepository;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@ConditionalOnProperty(prefix = "spotai.ai.review-summary", name = "enabled", havingValue = "true")
public class ReviewRagIndexService {
    private static final Path2 ROOT_PATH = Path2.of("$");

    private final ReviewRepository reviewRepository;
    private final ReviewEmbeddingRepository embeddingRepository;
    private final EmbeddingModel embeddingModel;
    private final JedisPooled jedis;
    private final ObjectMapper objectMapper;
    private final ReviewAiProperties properties;

    public ReviewRagIndexService(ReviewRepository reviewRepository,
                                 ReviewEmbeddingRepository embeddingRepository,
                                 EmbeddingModel embeddingModel,
                                 JedisPooled jedis,
                                 ObjectMapper objectMapper,
                                 ReviewAiProperties properties) {
        this.reviewRepository = reviewRepository;
        this.embeddingRepository = embeddingRepository;
        this.embeddingModel = embeddingModel;
        this.jedis = jedis;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public int indexMissingReviews(Long shopId) {
        List<Review> reviews = reviewRepository.findActiveWithContentByShopId(
                shopId,
                properties.getMaxIndexReviews()
        );
        int indexed = 0;
        for (Review review : reviews) {
            ReviewEmbedding existing = embeddingRepository.findByReviewId(review.getId());
            if (isCurrentAndIndexed(existing)) {
                continue;
            }

            float[] vector = loadOrCreateVector(review, existing);
            writeRedisDocument(review, vector);
            embeddingRepository.markRedisIndexed(review.getId());
            indexed++;
        }
        return indexed;
    }

    public int reindexShop(Long shopId) {
        for (String embeddingId : embeddingRepository.findEmbeddingIdsByShopId(shopId)) {
            jedis.jsonDel(properties.getKeyPrefix() + embeddingId);
        }
        embeddingRepository.markShopPending(shopId);
        return indexMissingReviews(shopId);
    }

    private boolean isCurrentAndIndexed(ReviewEmbedding existing) {
        return existing != null
                && existing.isRedisIndexed()
                && properties.getEmbeddingModel().equals(existing.getEmbeddingModel())
                && existing.getEmbeddingJson() != null;
    }

    private float[] loadOrCreateVector(Review review, ReviewEmbedding existing) {
        if (existing != null
                && properties.getEmbeddingModel().equals(existing.getEmbeddingModel())
                && existing.getEmbeddingJson() != null) {
            try {
                return objectMapper.readValue(existing.getEmbeddingJson(), float[].class);
            }
            catch (JsonProcessingException ignored) {
                // Invalid persisted data is replaced by a fresh embedding below.
            }
        }

        float[] vector = embeddingModel.embed(review.getContent());
        try {
            embeddingRepository.upsertEmbedding(
                    review,
                    String.valueOf(review.getId()),
                    properties.getEmbeddingModel(),
                    objectMapper.writeValueAsString(vector)
            );
            return vector;
        }
        catch (JsonProcessingException e) {
            throw new IllegalStateException("无法保存评论向量", e);
        }
    }

    private void writeRedisDocument(Review review, float[] vector) {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("content", review.getContent());
        fields.put("embedding", vector);
        fields.put("reviewId", String.valueOf(review.getId()));
        fields.put("shopId", String.valueOf(review.getShopId()));
        fields.put("score", review.getScore() == null ? 0 : review.getScore());
        fields.put("createTime", review.getCreateTime() == null ? "" : review.getCreateTime().toString());

        String response = jedis.jsonSetWithEscape(
                properties.getKeyPrefix() + review.getId(),
                ROOT_PATH,
                fields
        );
        if (!"OK".equals(response)) {
            throw new IllegalStateException("Redis Stack 写入评论向量失败");
        }
    }
}
