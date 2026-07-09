package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.ReviewSummaryRefreshMessage;
import com.tars.spotai.repository.ReviewSummaryRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.Set;
import java.time.LocalDateTime;

@Service
public class ReviewSummaryRefreshScheduler {
    private static final Logger log = LoggerFactory.getLogger(ReviewSummaryRefreshScheduler.class);

    private final ReviewSummaryService summaryService;
    private final ReviewSummaryRepository summaryRepository;
    private final MqEventPublisher mqEventPublisher;
    private final MqEventProperties mqEventProperties;

    @Value("${spotai.ai.review-summary.refresh-batch-size:30}")
    private int refreshBatchSize;

    public ReviewSummaryRefreshScheduler(ReviewSummaryService summaryService,
                                         ReviewSummaryRepository summaryRepository,
                                         MqEventPublisher mqEventPublisher,
                                         MqEventProperties mqEventProperties) {
        this.summaryService = summaryService;
        this.summaryRepository = summaryRepository;
        this.mqEventPublisher = mqEventPublisher;
        this.mqEventProperties = mqEventProperties;
    }

    @Async
    public void refreshAsync(Long shopId) {
        ReviewSummaryRefreshMessage message = new ReviewSummaryRefreshMessage(
                shopId,
                "REVIEW_CHANGED",
                LocalDateTime.now()
        );
        mqEventPublisher.publishOrRun(
                mqEventProperties.getReviewSummaryTopic(),
                message,
                () -> refreshDirect(shopId)
        );
    }

    public void refreshDirect(Long shopId) {
        try {
            summaryService.refreshSummaryNow(shopId);
        } catch (Exception e) {
            log.warn("Failed to refresh review summary asynchronously for shop {}", shopId, e);
        }
    }

    @Scheduled(fixedDelayString = "${spotai.ai.review-summary.refresh-scan-delay-ms:60000}",
            initialDelayString = "${spotai.ai.review-summary.refresh-scan-initial-delay-ms:10000}")
    public void refreshPendingSummaries() {
        int safeBatchSize = Math.max(1, Math.min(refreshBatchSize, 100));
        Set<Long> shopIds = new LinkedHashSet<>();
        shopIds.addAll(summaryRepository.findShopIdsNeedingRefresh(safeBatchSize));
        shopIds.addAll(summaryRepository.findShopIdsWithoutSummary(safeBatchSize));
        if (shopIds.isEmpty()) {
            log.debug("No shops need review summary refresh");
            return;
        }
        log.info("Scheduling review summary refresh for {} shops", shopIds.size());
        for (Long shopId : shopIds) {
            refreshAsync(shopId);
        }
    }
}
