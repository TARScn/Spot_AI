package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.ReviewSummary;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ReviewSummaryRepository;
import com.tars.spotai.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReviewSummaryServiceTest {

    @Mock
    private ReviewRepository reviewRepository;
    @Mock
    private ReviewSummaryRepository summaryRepository;
    @Mock
    private ShopRepository shopRepository;
    @Mock
    private ReviewRagIndexService ragIndexService;
    @Mock
    private ReviewSummaryGenerator summaryGenerator;
    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
    private ReviewAiProperties properties;

    @BeforeEach
    void setUp() {
        properties = new ReviewAiProperties();
        properties.setEnabled(true);
        properties.setMinReviewCount(3);
        properties.setCacheTtlMinutes(60);
    }

    @Test
    void rejectsInvalidShopId() {
        ReviewSummaryService service = service(Optional.of(summaryGenerator));

        Result<ReviewSummaryDTO> result = service.querySummary(0L);

        assertThat(result.isSuccess()).isFalse();
        verify(shopRepository, never()).findById(any());
    }

    @Test
    void returnsCachedSummaryWithoutCallingAi() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReviewSummaryDTO cached = ReviewSummaryDTO.ready(
                1L,
                "cached summary",
                List.of("taste"),
                List.of("queue"),
                List.of("friends"),
                12
        );
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(valueOperations.get("review:summary:1")).thenReturn(objectMapper.writeValueAsString(cached));

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getSummary()).isEqualTo("cached summary");
        verify(summaryRepository, never()).findByShopId(any());
        verify(reviewRepository, never()).countActiveWithContentByShopId(any());
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void returnsPersistedSummaryWithoutCallingAi() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        ReviewSummary persisted = persistedReadySummary();
        when(summaryRepository.findByShopId(1L)).thenReturn(persisted);

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getSummary()).isEqualTo("persisted summary");
        assertThat(result.getData().getHighlights()).containsExactly("stable");
        assertThat(result.getData().getScenes()).containsExactly("date");
        verify(reviewRepository, never()).countActiveWithContentByShopId(any());
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void returnsPersistedSummaryEvenWhenExpireAtIsInThePast() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        ReviewSummary persisted = persistedReadySummary();
        persisted.setExpireAt(LocalDateTime.now().minusDays(1));
        when(summaryRepository.findByShopId(1L)).thenReturn(persisted);

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_READY);
        assertThat(result.getData().getSummary()).isEqualTo("persisted summary");
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void querySummaryGeneratesImmediatelyWhenSummaryIsMissing() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(8);
        when(summaryGenerator.generate(1L)).thenReturn(new ReviewSummaryContent(
                "generated on first view",
                List.of("fresh"),
                List.of(),
                List.of("friends")
        ));

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_READY);
        assertThat(result.getData().getSummary()).isEqualTo("generated on first view");
        verify(ragIndexService).indexMissingReviews(1L);
        verify(summaryGenerator).generate(1L);
    }

    @Test
    void returnsInsufficientStatusWhenThereAreTooFewReviewsAndPersistsIt() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(2);

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_INSUFFICIENT_REVIEWS);
        assertThat(result.getData().getReviewCount()).isEqualTo(2);
        verify(summaryRepository).upsert(any(ReviewSummary.class));
        verify(ragIndexService, never()).indexMissingReviews(any());
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void refreshSummaryIndexesReviewsGeneratesSummaryPersistsAndCachesResult() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(8);
        when(summaryGenerator.generate(1L)).thenReturn(new ReviewSummaryContent(
                "generated summary",
                List.of("stable"),
                List.of("queue"),
                List.of("family")
        ));

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).refreshSummaryNow(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_READY);
        assertThat(result.getData().getSummary()).isEqualTo("generated summary");
        verify(summaryRepository).markBuilding(1L);
        verify(summaryRepository).upsert(any(ReviewSummary.class));
        verify(ragIndexService).indexMissingReviews(1L);
        verify(valueOperations).set(
                eq("review:summary:1"),
                any(String.class),
                eq(60L),
                eq(TimeUnit.MINUTES)
        );
    }

    @Test
    void markStaleUpdatesMysqlAndDeletesRedisCache() {
        ReviewSummaryService service = service(Optional.of(summaryGenerator));

        service.markStale(1L);

        verify(summaryRepository).markStale(1L);
        verify(redisTemplate).delete("review:summary:1");
    }

    private ReviewSummaryService service(Optional<ReviewSummaryGenerator> generator) {
        return new ReviewSummaryService(
                reviewRepository,
                summaryRepository,
                shopRepository,
                Optional.of(ragIndexService),
                generator,
                redisTemplate,
                objectMapper,
                properties
        );
    }

    private ReviewSummary persistedReadySummary() {
        ReviewSummary persisted = new ReviewSummary();
        persisted.setShopId(1L);
        persisted.setStatus(ReviewSummaryDTO.STATUS_READY);
        persisted.setSummary("persisted summary");
        persisted.setHighlightsJson("[\"stable\"]");
        persisted.setWeaknessesJson("[]");
        persisted.setScenesJson("[\"date\"]");
        persisted.setReviewCount(9);
        return persisted;
    }

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("Test shop");
        return shop;
    }
}
