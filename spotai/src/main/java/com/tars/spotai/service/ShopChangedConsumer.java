package com.tars.spotai.service;

import com.tars.spotai.dto.ShopChangedMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费商户变更事件，异步刷新 Redis 缓存和 GEO 索引。
 */
@Component
@ConditionalOnProperty(prefix = "spotai.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.mq.shop-changed-topic}",
        consumerGroup = "spotai-shop-changed-consumer"
)
public class ShopChangedConsumer implements RocketMQListener<ShopChangedMessage> {
    private final ShopService shopService;

    public ShopChangedConsumer(ShopService shopService) {
        this.shopService = shopService;
    }

    @Override
    public void onMessage(ShopChangedMessage message) {
        shopService.handleShopChanged(message.getShopId());
    }
}
