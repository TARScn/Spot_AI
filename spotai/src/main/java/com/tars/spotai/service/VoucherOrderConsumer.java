package com.tars.spotai.service;

import com.tars.spotai.dto.VoucherOrderMessage;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "spotai.voucher", name = "mq-enabled", havingValue = "true", matchIfMissing = true)
@RocketMQMessageListener(
        topic = "${spotai.voucher.order-topic}",
        consumerGroup = "${spotai.voucher.order-consumer-group}"
)
public class VoucherOrderConsumer implements RocketMQListener<VoucherOrderMessage> {
    private final VoucherService voucherService;

    public VoucherOrderConsumer(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @Override
    public void onMessage(VoucherOrderMessage message) {
        voucherService.handleVoucherOrder(message);
    }
}
