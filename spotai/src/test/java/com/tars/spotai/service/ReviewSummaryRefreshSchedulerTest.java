package com.tars.spotai.service;

import com.tars.spotai.repository.ReviewSummaryRepository;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReviewSummaryRefreshSchedulerTest {

    @Test
    void scansStaleAndMissingSummaryShops() {
        ReviewSummaryService summaryService = mock(ReviewSummaryService.class);
        ReviewSummaryRepository summaryRepository = mock(ReviewSummaryRepository.class);
        when(summaryRepository.findShopIdsNeedingRefresh(10)).thenReturn(List.of(1L));
        when(summaryRepository.findShopIdsWithoutSummary(10)).thenReturn(List.of(2L));
        ReviewSummaryRefreshScheduler scheduler = new ReviewSummaryRefreshScheduler(summaryService, summaryRepository);
        ReflectionTestUtils.setField(scheduler, "refreshBatchSize", 10);

        scheduler.refreshPendingSummaries();

        verify(summaryService).refreshSummaryNow(1L);
        verify(summaryService).refreshSummaryNow(2L);
    }
}
