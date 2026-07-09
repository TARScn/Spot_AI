package com.tars.spotai.service;

import com.tars.spotai.dto.UvRecordMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费 UV 上报事件，异步写入 Redis HyperLogLog。
 */
@Component
@ConditionalOnProperty(prefix = "spotai.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.mq.uv-record-topic}",
        consumerGroup = "spotai-uv-record-consumer"
)
public class UvRecordConsumer implements RocketMQListener<UvRecordMessage> {
    private final UvStatsService uvStatsService;

    public UvRecordConsumer(UvStatsService uvStatsService) {
        this.uvStatsService = uvStatsService;
    }

    @Override
    public void onMessage(UvRecordMessage message) {
        uvStatsService.recordDirect(message);
    }
}
