package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ReviewSummaryService {
    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryService.class);
    private static final String CACHE_KEY_PREFIX = "review:summary:";

    private final ReviewRepository reviewRepository;
    private final ShopRepository shopRepository;
    private final Optional<ReviewRagIndexService> ragIndexService;
    private final Optional<ReviewSummaryGenerator> summaryGenerator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReviewAiProperties properties;

    public ReviewSummaryService(ReviewRepository reviewRepository,
                                ShopRepository shopRepository,
                                Optional<ReviewRagIndexService> ragIndexService,
                                Optional<ReviewSummaryGenerator> summaryGenerator,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                ReviewAiProperties properties) {
        this.reviewRepository = reviewRepository;
        this.shopRepository = shopRepository;
        this.ragIndexService = ragIndexService;
        this.summaryGenerator = summaryGenerator;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Result<ReviewSummaryDTO> querySummary(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("商户 ID 不合法");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("商户不存在");
        }

        ReviewSummaryDTO cached = readCache(shopId);
        if (cached != null) {
            return Result.ok(cached);
        }

        int reviewCount = reviewRepository.countActiveWithContentByShopId(shopId);
        if (reviewCount < properties.getMinReviewCount()) {
            return Result.ok(ReviewSummaryDTO.insufficient(shopId, reviewCount));
        }
        if (!properties.isEnabled() || summaryGenerator.isEmpty() || ragIndexService.isEmpty()) {
            return Result.ok(ReviewSummaryDTO.unavailable(shopId, reviewCount));
        }

        try {
            ragIndexService.get().indexMissingReviews(shopId);
            ReviewSummaryContent content = summaryGenerator.get().generate(shopId);
            ReviewSummaryDTO summary = ReviewSummaryDTO.ready(
                    shopId,
                    content.summary(),
                    content.highlights(),
                    content.weaknesses(),
                    content.scenes(),
                    reviewCount
            );
            writeCache(summary);
            return Result.ok(summary);
        }
        catch (Exception e) {
            log.error("Failed to generate review summary for shop {}", shopId, e);
            return Result.ok(ReviewSummaryDTO.unavailable(shopId, reviewCount));
        }
    }

    public void evictSummary(Long shopId) {
        if (shopId != null && shopId > 0) {
            redisTemplate.delete(cacheKey(shopId));
        }
    }

    private ReviewSummaryDTO readCache(Long shopId) {
        String value = redisTemplate.opsForValue().get(cacheKey(shopId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, ReviewSummaryDTO.class);
        }
        catch (JsonProcessingException e) {
            redisTemplate.delete(cacheKey(shopId));
            return null;
        }
    }

    private void writeCache(ReviewSummaryDTO summary) throws JsonProcessingException {
        redisTemplate.opsForValue().set(
                cacheKey(summary.getShopId()),
                objectMapper.writeValueAsString(summary),
                properties.getCacheTtlMinutes(),
                TimeUnit.MINUTES
        );
    }

    private String cacheKey(Long shopId) {
        return CACHE_KEY_PREFIX + shopId;
    }
}
