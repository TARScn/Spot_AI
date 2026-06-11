package com.tars.spotai.service;

import com.tars.spotai.dto.VoucherOrderMessage;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class VoucherOrderConsumer {
    private final VoucherService voucherService;

    public VoucherOrderConsumer(VoucherService voucherService) {
        this.voucherService = voucherService;
    }

    @KafkaListener(
            topics = "${spotai.voucher.order-topic}",
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(VoucherOrderMessage message) {
        voucherService.handleVoucherOrder(message);
    }
}
