package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "spotai.voucher")
public class VoucherProperties {
    private String orderTopic = "spotai.voucher-order.create";
    private String orderDltTopic = "spotai.voucher-order.create.dlt";

    public String getOrderTopic() {
        return orderTopic;
    }

    public void setOrderTopic(String orderTopic) {
        this.orderTopic = orderTopic;
    }

    public String getOrderDltTopic() {
        return orderDltTopic;
    }

    public void setOrderDltTopic(String orderDltTopic) {
        this.orderDltTopic = orderDltTopic;
    }
}
