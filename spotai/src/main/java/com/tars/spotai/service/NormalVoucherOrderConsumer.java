package com.tars.spotai.service;

import com.tars.spotai.dto.NormalVoucherOrderMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 消费普通代金券领取事件，异步创建用户券订单。
 */
@Component
@ConditionalOnProperty(prefix = "spotai.mq", name = "enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.mq.normal-voucher-order-topic}",
        consumerGroup = "spotai-normal-voucher-order-consumer"
)
public class NormalVoucherOrderConsumer implements RocketMQListener<NormalVoucherOrderMessage> {
    private final VoucherService voucherService;

    public NormalVoucherOrderConsumer(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Override
    public void onMessage(NormalVoucherOrderMessage message) {
        voucherService.handleNormalVoucherOrder(message);
    }
}
