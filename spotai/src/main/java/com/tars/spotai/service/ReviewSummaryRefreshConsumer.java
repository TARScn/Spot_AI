package com.tars.spotai.service;

import com.tars.spotai.dto.ReviewSummaryRefreshMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费评价摘要刷新事件，异步生成并写回 MySQL/Redis。
 */
@Component
@ConditionalOnProperty(prefix = "spotai.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.mq.review-summary-topic}",
        consumerGroup = "spotai-review-summary-refresh-consumer"
)
public class ReviewSummaryRefreshConsumer implements RocketMQListener<ReviewSummaryRefreshMessage> {
    private final ReviewSummaryRefreshScheduler scheduler;

    public ReviewSummaryRefreshConsumer(ReviewSummaryRefreshScheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public void onMessage(ReviewSummaryRefreshMessage message) {
        scheduler.refreshDirect(message.getShopId());
    }
}
