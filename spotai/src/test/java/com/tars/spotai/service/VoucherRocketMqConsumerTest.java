package com.tars.spotai.service;

import com.tars.spotai.dto.VoucherOrderMessage;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class VoucherRocketMqConsumerTest {
    @Test
    void onMessageDelegatesToVoucherService() {
        VoucherService voucherService = mock(VoucherService.class);
        VoucherOrderConsumer consumer = new VoucherOrderConsumer(voucherService);
        VoucherOrderMessage message = new VoucherOrderMessage(
                9001L,
                1001L,
                2001L,
                9001L,
                LocalDateTime.now()
        );

        consumer.onMessage(message);

        verify(voucherService).handleVoucherOrder(message);
    }
}
