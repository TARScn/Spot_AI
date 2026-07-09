package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class MqEventPublisherTest {

    @Test
    void sendsMessageWhenMqEnabled() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqEventPublisher publisher = new MqEventPublisher(rocketMQTemplate, new MqEventProperties());
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        publisher.publishOrRun("topic.demo", "payload", () -> fallbackCalled.set(true));

        verify(rocketMQTemplate).syncSend(eq("topic.demo"), eq("payload"), anyLong());
        assertThat(fallbackCalled).isFalse();
    }

    @Test
    void runsFallbackWhenMqDisabled() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        MqEventProperties properties = new MqEventProperties();
        properties.setEnabled(false);
        MqEventPublisher publisher = new MqEventPublisher(rocketMQTemplate, properties);
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        publisher.publishOrRun("topic.demo", "payload", () -> fallbackCalled.set(true));

        verify(rocketMQTemplate, never()).syncSend(eq("topic.demo"), eq("payload"), anyLong());
        assertThat(fallbackCalled).isTrue();
    }

    @Test
    void runsFallbackWhenMqSendFails() {
        RocketMQTemplate rocketMQTemplate = mock(RocketMQTemplate.class);
        doThrow(new IllegalStateException("mq down"))
                .when(rocketMQTemplate)
                .syncSend(eq("topic.demo"), eq("payload"), anyLong());
        MqEventPublisher publisher = new MqEventPublisher(rocketMQTemplate, new MqEventProperties());
        AtomicBoolean fallbackCalled = new AtomicBoolean(false);

        publisher.publishOrRun("topic.demo", "payload", () -> fallbackCalled.set(true));

        assertThat(fallbackCalled).isTrue();
    }
}
