package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.entity.Review;
import com.tars.spotai.entity.ReviewEmbedding;
import com.tars.spotai.repository.ReviewEmbeddingRepository;
import com.tars.spotai.repository.ReviewRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.embedding.EmbeddingModel;
import redis.clients.jedis.JedisPooled;
import redis.clients.jedis.json.Path2;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewRagIndexServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewEmbeddingRepository embeddingRepository;
    @Mock
    private EmbeddingModel embeddingModel;
    @Mock
    private JedisPooled jedis;

    private ReviewAiProperties properties;
    private ReviewRagIndexService service;

    @BeforeEach
    void setUp() {
        properties = new ReviewAiProperties();
        properties.setEnabled(true);
        properties.setEmbeddingModel("text-embedding-v3");
        properties.setKeyPrefix("review:vector:");
        service = new ReviewRagIndexService(
                reviewRepository,
                embeddingRepository,
                embeddingModel,
                jedis,
                new ObjectMapper(),
                properties
        );
    }

    @Test
    void createsEmbeddingPersistsItAndWritesRedisDocument() {
        Review review = review(101L, 7L, "牛肉很嫩，服务也很热情");
        when(reviewRepository.findActiveWithContentByShopId(7L, 500)).thenReturn(List.of(review));
        when(embeddingModel.embed(review.getContent())).thenReturn(new float[]{0.1f, 0.2f});
        when(jedis.jsonSetWithEscape(eq("review:vector:101"), eq(Path2.of("$")), any(Map.class)))
                .thenReturn("OK");

        int indexed = service.indexMissingReviews(7L);

        assertThat(indexed).isEqualTo(1);
        verify(embeddingRepository).upsertEmbedding(
                eq(review),
                eq("101"),
                eq("text-embedding-v3"),
                eq("[0.1,0.2]")
        );
        verify(embeddingRepository).markRedisIndexed(101L);
    }

    @Test
    void skipsReviewThatIsAlreadyIndexedWithCurrentModel() {
        Review review = review(101L, 7L, "环境安静");
        ReviewEmbedding existing = embedding(review, "[0.1,0.2]", true);
        when(reviewRepository.findActiveWithContentByShopId(7L, 500)).thenReturn(List.of(review));
        when(embeddingRepository.findByReviewId(101L)).thenReturn(existing);

        int indexed = service.indexMissingReviews(7L);

        assertThat(indexed).isZero();
        verify(embeddingModel, never()).embed(any(String.class));
        verify(jedis, never()).jsonSetWithEscape(
                any(String.class),
                any(Path2.class),
                any(Object.class)
        );
    }

    @Test
    void reusesMysqlEmbeddingWhenRebuildingRedisIndex() {
        Review review = review(101L, 7L, "环境安静");
        ReviewEmbedding existing = embedding(review, "[0.3,0.4]", false);
        when(reviewRepository.findActiveWithContentByShopId(7L, 500)).thenReturn(List.of(review));
        when(embeddingRepository.findByReviewId(101L)).thenReturn(existing);
        when(jedis.jsonSetWithEscape(eq("review:vector:101"), eq(Path2.of("$")), any(Map.class)))
                .thenReturn("OK");

        int indexed = service.indexMissingReviews(7L);

        assertThat(indexed).isEqualTo(1);
        verify(embeddingModel, never()).embed(any(String.class));
        verify(embeddingRepository, never()).upsertEmbedding(any(), any(), any(), any());
        verify(embeddingRepository).markRedisIndexed(101L);
    }

    private Review review(Long id, Long shopId, String content) {
        Review review = new Review();
        review.setId(id);
        review.setShopId(shopId);
        review.setScore(5);
        review.setContent(content);
        review.setCreateTime(LocalDateTime.of(2026, 6, 24, 12, 0));
        return review;
    }

    private ReviewEmbedding embedding(Review review, String vector, boolean indexed) {
        ReviewEmbedding embedding = new ReviewEmbedding();
        embedding.setReviewId(review.getId());
        embedding.setShopId(review.getShopId());
        embedding.setChunkText(review.getContent());
        embedding.setEmbeddingId(String.valueOf(review.getId()));
        embedding.setEmbeddingModel("text-embedding-v3");
        embedding.setEmbeddingJson(vector);
        embedding.setRedisIndexed(indexed);
        return embedding;
    }
}
