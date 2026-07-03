package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.ReviewSummary;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ReviewSummaryRepository;
import com.tars.spotai.repository.ShopRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
public class ReviewSummaryService {
    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryService.class);
    private static final String CACHE_KEY_PREFIX = "review:summary:";
    private static final String STATUS_STALE = "STALE";
    private static final String STATUS_BUILDING = "BUILDING";
    private static final String MESSAGE_PREPARING = "AI summary is being prepared";
    private static final String MESSAGE_INSUFFICIENT = "Not enough reviews to generate an AI summary";
    private static final String MESSAGE_UNAVAILABLE = "AI summary is temporarily unavailable";

    private final ReviewRepository reviewRepository;
    private final ReviewSummaryRepository summaryRepository;
    private final ShopRepository shopRepository;
    private final Optional<ReviewRagIndexService> ragIndexService;
    private final Optional<ReviewSummaryGenerator> summaryGenerator;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ReviewAiProperties properties;

    public ReviewSummaryService(ReviewRepository reviewRepository,
                                ReviewSummaryRepository summaryRepository,
                                ShopRepository shopRepository,
                                Optional<ReviewRagIndexService> ragIndexService,
                                Optional<ReviewSummaryGenerator> summaryGenerator,
                                StringRedisTemplate redisTemplate,
                                ObjectMapper objectMapper,
                                ReviewAiProperties properties) {
        this.reviewRepository = reviewRepository;
        this.summaryRepository = summaryRepository;
        this.shopRepository = shopRepository;
        this.ragIndexService = ragIndexService;
        this.summaryGenerator = summaryGenerator;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public Result<ReviewSummaryDTO> querySummary(Long shopId) {
        Result<Void> validation = validateShop(shopId);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }

        ReviewSummaryDTO cached = readCache(shopId);
        if (cached != null) {
            return Result.ok(cached);
        }

        ReviewSummary persisted = summaryRepository.findByShopId(shopId);
        ReviewSummaryDTO persistedDto = toDto(persisted);
        if (persistedDto != null && shouldServe(persisted)) {
            writeCacheQuietly(persistedDto);
            return Result.ok(persistedDto);
        }

        int reviewCount = reviewRepository.countActiveWithContentByShopId(shopId);
        if (persisted != null && STATUS_BUILDING.equals(persisted.getStatus())) {
            return Result.ok(preparing(shopId, reviewCount));
        }
        if (reviewCount < properties.getMinReviewCount()) {
            ReviewSummaryDTO summary = insufficient(shopId, reviewCount);
            saveSummary(summary, null);
            return Result.ok(summary);
        }
        return refreshSummaryNow(shopId);
    }

    public Result<ReviewSummaryDTO> refreshSummaryNow(Long shopId) {
        Result<Void> validation = validateShop(shopId);
        if (!validation.isSuccess()) {
            return Result.fail(validation.getErrorMsg());
        }

        int reviewCount = reviewRepository.countActiveWithContentByShopId(shopId);
        if (reviewCount < properties.getMinReviewCount()) {
            ReviewSummaryDTO summary = insufficient(shopId, reviewCount);
            saveSummary(summary, null);
            return Result.ok(summary);
        }
        if (!properties.isEnabled() || summaryGenerator.isEmpty() || ragIndexService.isEmpty()) {
            ReviewSummaryDTO summary = unavailable(shopId, reviewCount);
            saveSummary(summary, null);
            return Result.ok(summary);
        }

        summaryRepository.markBuilding(shopId);
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
            saveSummary(summary, null);
            writeCache(summary);
            return Result.ok(summary);
        } catch (Exception e) {
            log.error("Failed to generate review summary for shop {}", shopId, e);
            ReviewSummaryDTO summary = unavailable(shopId, reviewCount);
            saveSummary(summary, null);
            return Result.ok(summary);
        }
    }

    public Optional<ReviewSummaryDTO> queryCachedSummary(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Optional.empty();
        }
        ReviewSummaryDTO cached = readCache(shopId);
        if (cached != null) {
            return Optional.of(cached);
        }
        ReviewSummary persisted = summaryRepository.findByShopId(shopId);
        ReviewSummaryDTO persistedDto = toDto(persisted);
        if (persistedDto == null || !shouldServe(persisted)) {
            return Optional.empty();
        }
        return Optional.of(persistedDto);
    }

    public void markStale(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return;
        }
        summaryRepository.markStale(shopId);
        evictSummary(shopId);
    }

    public void evictSummary(Long shopId) {
        if (shopId != null && shopId > 0) {
            redisTemplate.delete(cacheKey(shopId));
        }
    }

    private Result<Void> validateShop(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return Result.fail("Invalid shop id");
        }
        if (shopRepository.findById(shopId) == null) {
            return Result.fail("Shop not found");
        }
        return Result.ok(null);
    }

    private ReviewSummaryDTO readCache(Long shopId) {
        String value = redisTemplate.opsForValue().get(cacheKey(shopId));
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(value, ReviewSummaryDTO.class);
        } catch (JsonProcessingException e) {
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

    private void writeCacheQuietly(ReviewSummaryDTO summary) {
        try {
            writeCache(summary);
        } catch (Exception e) {
            log.debug("Failed to write review summary cache for shop {}", summary.getShopId(), e);
        }
    }

    private void saveSummary(ReviewSummaryDTO dto, LocalDateTime expireAt) {
        try {
            ReviewSummary row = new ReviewSummary();
            row.setShopId(dto.getShopId());
            row.setStatus(dto.getStatus());
            row.setSummary(dto.getSummary());
            row.setHighlightsJson(objectMapper.writeValueAsString(safeList(dto.getHighlights())));
            row.setWeaknessesJson(objectMapper.writeValueAsString(safeList(dto.getWeaknesses())));
            row.setScenesJson(objectMapper.writeValueAsString(safeList(dto.getScenes())));
            row.setReviewCount(dto.getReviewCount());
            row.setVersion(1);
            row.setGeneratedAt(dto.getGeneratedAt());
            row.setExpireAt(expireAt);
            summaryRepository.upsert(row);
        } catch (Exception e) {
            log.warn("Failed to persist review summary for shop {}", dto.getShopId(), e);
        }
    }

    private ReviewSummaryDTO toDto(ReviewSummary row) {
        if (row == null || row.getStatus() == null) {
            return null;
        }
        ReviewSummaryDTO dto = new ReviewSummaryDTO();
        dto.setShopId(row.getShopId());
        dto.setStatus(row.getStatus());
        dto.setSummary(row.getSummary());
        dto.setHighlights(readStringList(row.getHighlightsJson()));
        dto.setWeaknesses(readStringList(row.getWeaknessesJson()));
        dto.setScenes(readStringList(row.getScenesJson()));
        dto.setReviewCount(row.getReviewCount() == null ? 0 : row.getReviewCount());
        dto.setGeneratedAt(row.getGeneratedAt());
        if (ReviewSummaryDTO.STATUS_INSUFFICIENT_REVIEWS.equals(row.getStatus())) {
            dto.setMessage(MESSAGE_INSUFFICIENT);
        } else if (ReviewSummaryDTO.STATUS_UNAVAILABLE.equals(row.getStatus())) {
            dto.setMessage(MESSAGE_UNAVAILABLE);
        } else if (STATUS_STALE.equals(row.getStatus()) || STATUS_BUILDING.equals(row.getStatus())) {
            dto.setStatus(ReviewSummaryDTO.STATUS_UNAVAILABLE);
            dto.setMessage(MESSAGE_PREPARING);
        }
        return dto;
    }

    private boolean shouldServe(ReviewSummary row) {
        if (row == null) {
            return false;
        }
        if (STATUS_STALE.equals(row.getStatus()) || STATUS_BUILDING.equals(row.getStatus())) {
            return false;
        }
        return true;
    }

    private ReviewSummaryDTO preparing(Long shopId, int reviewCount) {
        ReviewSummaryDTO dto = ReviewSummaryDTO.unavailable(shopId, reviewCount);
        dto.setMessage(MESSAGE_PREPARING);
        return dto;
    }

    private ReviewSummaryDTO insufficient(Long shopId, int reviewCount) {
        ReviewSummaryDTO dto = ReviewSummaryDTO.insufficient(shopId, reviewCount);
        dto.setMessage(MESSAGE_INSUFFICIENT);
        return dto;
    }

    private ReviewSummaryDTO unavailable(Long shopId, int reviewCount) {
        ReviewSummaryDTO dto = ReviewSummaryDTO.unavailable(shopId, reviewCount);
        dto.setMessage(MESSAGE_UNAVAILABLE);
        return dto;
    }

    private List<String> readStringList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (Exception e) {
            return List.of();
        }
    }

    private List<String> safeList(List<String> values) {
        return values == null ? List.of() : values;
    }

    private String cacheKey(Long shopId) {
        return CACHE_KEY_PREFIX + shopId;
    }
}
