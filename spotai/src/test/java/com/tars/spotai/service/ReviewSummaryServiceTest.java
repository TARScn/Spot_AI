package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.config.ReviewAiProperties;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.repository.ReviewRepository;
import com.tars.spotai.repository.ShopRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

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
                "整体评价稳定",
                List.of("口味好"),
                List.of("高峰期排队"),
                List.of("朋友聚餐"),
                12
        );
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(valueOperations.get("review:summary:1")).thenReturn(objectMapper.writeValueAsString(cached));

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getSummary()).isEqualTo("整体评价稳定");
        verify(reviewRepository, never()).countActiveWithContentByShopId(any());
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void returnsInsufficientStatusWhenThereAreTooFewReviews() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(2);

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_INSUFFICIENT_REVIEWS);
        assertThat(result.getData().getReviewCount()).isEqualTo(2);
        verify(ragIndexService, never()).indexMissingReviews(any());
        verify(summaryGenerator, never()).generate(any());
    }

    @Test
    void returnsUnavailableStatusWhenAiIsDisabled() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        properties.setEnabled(false);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(8);

        Result<ReviewSummaryDTO> result = service(Optional.empty()).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_UNAVAILABLE);
        verify(ragIndexService, never()).indexMissingReviews(any());
    }

    @Test
    void indexesReviewsGeneratesSummaryAndCachesResult() throws Exception {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(shopRepository.findById(1L)).thenReturn(shop(1L));
        when(reviewRepository.countActiveWithContentByShopId(1L)).thenReturn(8);
        when(summaryGenerator.generate(1L)).thenReturn(new ReviewSummaryContent(
                "整体评价较好",
                List.of("口味稳定"),
                List.of("排队较久"),
                List.of("家庭聚餐")
        ));

        Result<ReviewSummaryDTO> result = service(Optional.of(summaryGenerator)).querySummary(1L);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getData().getStatus()).isEqualTo(ReviewSummaryDTO.STATUS_READY);
        assertThat(result.getData().getReviewCount()).isEqualTo(8);
        assertThat(result.getData().getSummary()).isEqualTo("整体评价较好");
        verify(ragIndexService).indexMissingReviews(1L);
        verify(valueOperations).set(
                eq("review:summary:1"),
                any(String.class),
                eq(60L),
                eq(TimeUnit.MINUTES)
        );
    }

    private ReviewSummaryService service(Optional<ReviewSummaryGenerator> generator) {
        return new ReviewSummaryService(
                reviewRepository,
                shopRepository,
                Optional.of(ragIndexService),
                generator,
                redisTemplate,
                objectMapper,
                properties
        );
    }

    private Shop shop(Long id) {
        Shop shop = new Shop();
        shop.setId(id);
        shop.setName("测试商户");
        return shop;
    }
}
