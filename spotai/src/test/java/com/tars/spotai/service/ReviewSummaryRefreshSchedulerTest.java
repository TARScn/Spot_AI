package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import com.tars.spotai.dto.ReviewSummaryRefreshMessage;
import com.tars.spotai.repository.ReviewSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewSummaryRefreshSchedulerTest {

    @Test
    void scansStaleAndMissingSummaryShops() {
        ReviewSummaryService summaryService = mock(ReviewSummaryService.class);
        ReviewSummaryRepository summaryRepository = mock(ReviewSummaryRepository.class);
        MqEventPublisher mqEventPublisher = mock(MqEventPublisher.class);
        MqEventProperties mqEventProperties = new MqEventProperties();
        when(summaryRepository.findShopIdsNeedingRefresh(10)).thenReturn(List.of(1L));
        when(summaryRepository.findShopIdsWithoutSummary(10)).thenReturn(List.of(2L));
        ReviewSummaryRefreshScheduler scheduler = new ReviewSummaryRefreshScheduler(
                summaryService,
                summaryRepository,
                mqEventPublisher,
                mqEventProperties
        );
        ReflectionTestUtils.setField(scheduler, "refreshBatchSize", 10);

        scheduler.refreshPendingSummaries();

        verify(mqEventPublisher, times(2)).publishOrRun(
                eq("spotai.review-summary.refresh"),
                any(ReviewSummaryRefreshMessage.class),
                any(Runnable.class)
        );
    }

    @Test
    void consumerDelegatesToDirectRefresh() {
        ReviewSummaryRefreshScheduler scheduler = mock(ReviewSummaryRefreshScheduler.class);
        ReviewSummaryRefreshConsumer consumer = new ReviewSummaryRefreshConsumer(scheduler);
        ReviewSummaryRefreshMessage message = new ReviewSummaryRefreshMessage(1L, "TEST", null);

        consumer.onMessage(message);

        verify(scheduler).refreshDirect(1L);
    }
}
