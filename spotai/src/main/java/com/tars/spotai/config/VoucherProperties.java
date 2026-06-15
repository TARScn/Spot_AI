package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotai.voucher")
public class VoucherProperties {
    private String orderTopic = "spotai.voucher-order.create";
    private String orderConsumerGroup = "spotai-voucher-order-consumer";

    public String getOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(String orderTopic) {
        this.orderTopic = orderTopic;
    }

    public String getOrderConsumerGroup() {
        return orderConsumerGroup;
    }

    public void setOrderConsumerGroup(String orderConsumerGroup) {
        this.orderConsumerGroup = orderConsumerGroup;
    }
}
