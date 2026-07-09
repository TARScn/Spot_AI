package com.tars.spotai.service;

import com.tars.spotai.config.MqEventProperties;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 统一封装业务事件发送。
 *
 * <p>发送失败或 MQ 关闭时执行 fallback，保证核心业务不因 MQ 暂不可用而中断。
 * fallback 应保持幂等，因为 RocketMQ 消费端也可能发生重复消费。</p>
 */
@Service
public class MqEventPublisher {
    private static final Logger log = LoggerFactory.getLogger(MqEventPublisher.class);
    private static final long SEND_TIMEOUT_MS = 3000L;

    private final RocketMQTemplate rocketMQTemplate;
    private final MqEventProperties properties;

    public MqEventPublisher(RocketMQTemplate rocketMQTemplate, MqEventProperties properties) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.properties = properties;
    }

    public void publishOrRun(String topic, Object message, Runnable fallback) {
        if (!properties.isEnabled()) {
            fallback.run();
            return;
        }
        try {
            rocketMQTemplate.syncSend(topic, message, SEND_TIMEOUT_MS);
        } catch (Exception e) {
            log.warn("RocketMQ send failed, run fallback. topic={}, message={}", topic, message, e);
            fallback.run();
        }
    }
}
