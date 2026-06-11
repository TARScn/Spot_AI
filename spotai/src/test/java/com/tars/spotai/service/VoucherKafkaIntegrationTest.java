package com.tars.spotai.service;

import com.tars.spotai.dto.VoucherOrderMessage;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.timeout;

/**
 * Kafka integration test for the voucher order message flow.
 *
 * <p>This test uses the local Kafka broker configured in application.yml
 * (currently localhost:19092) and verifies that a real Kafka message can be
 * consumed by {@link VoucherOrderConsumer}.</p>
 */
@SpringBootTest(properties = {
        "spotai.voucher.order-topic=spotai.voucher-order.test",
        "spring.kafka.consumer.group-id=spotai-voucher-order-test",
        "spring.kafka.consumer.auto-offset-reset=earliest"
})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class VoucherKafkaIntegrationTest {
    private static final String TOPIC = "spotai.voucher-order.test";

    @BeforeAll
    void createTopic(@Value("${spring.kafka.bootstrap-servers}") String bootstrapServers) throws Exception {
        try (AdminClient adminClient = AdminClient.create(Map.of("bootstrap.servers", bootstrapServers))) {
            try {
                adminClient.createTopics(List.of(new NewTopic(TOPIC, 1, (short) 1)))
                        .all()
                        .get(10, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                if (!(e.getCause() instanceof TopicExistsException)) {
                    throw e;
                }
            }
        }
    }

    @Autowired
    private KafkaTemplate<String, VoucherOrderMessage> kafkaTemplate;

    @MockBean
    private VoucherService voucherService;

    @Test
    void consumerReceivesVoucherOrderMessageAndDelegatesToService() throws Exception {
        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(invocation -> {
            latch.countDown();
            return null;
        }).when(voucherService).handleVoucherOrder(any(VoucherOrderMessage.class));

        VoucherOrderMessage message = new VoucherOrderMessage(
                System.currentTimeMillis(),
                1001L,
                2001L,
                System.currentTimeMillis(),
                LocalDateTime.now()
        );

        kafkaTemplate.send(TOPIC, message.getOrderId().toString(), message).get(10, TimeUnit.SECONDS);

        assertThat(latch.await(10, TimeUnit.SECONDS)).isTrue();
        ArgumentCaptor<VoucherOrderMessage> captor = ArgumentCaptor.forClass(VoucherOrderMessage.class);
        verify(voucherService, timeout(1000).atLeastOnce()).handleVoucherOrder(captor.capture());
        assertThat(captor.getAllValues())
                .anySatisfy(consumedMessage -> {
                    assertThat(consumedMessage.getOrderId()).isEqualTo(message.getOrderId());
                    assertThat(consumedMessage.getUserId()).isEqualTo(message.getUserId());
                    assertThat(consumedMessage.getVoucherId()).isEqualTo(message.getVoucherId());
                    assertThat(consumedMessage.getTraceId()).isEqualTo(message.getTraceId());
                });
    }
}
