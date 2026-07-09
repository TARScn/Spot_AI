package com.tars.spotai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * SpotAI 业务事件 MQ 配置。
 *
 * <p>项目允许在没有 RocketMQ 的本地环境降级为同步执行，因此这里的 enabled 是业务事件统一开关。
 * 秒杀下单仍保留在 {@link VoucherProperties} 中，是为了兼容已有配置和测试。</p>
 */
@ConfigurationProperties(prefix = "spotai.mq")
public class MqEventProperties {
    private boolean enabled = true;
    private String reviewSummaryTopic = "spotai.review-summary.refresh";
    private String shopChangedTopic = "spotai.shop.changed";
    private String uvRecordTopic = "spotai.uv.record";
    private String blogPublishedTopic = "spotai.blog.published";
    private String normalVoucherOrderTopic = "spotai.voucher-normal-order.create";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getReviewSummaryTopic() {
        return reviewSummaryTopic;
    }

    public void setReviewSummaryTopic(String reviewSummaryTopic) {
        this.reviewSummaryTopic = reviewSummaryTopic;
    }

    public String getShopChangedTopic() {
        return shopChangedTopic;
    }

    public void setShopChangedTopic(String shopChangedTopic) {
        this.shopChangedTopic = shopChangedTopic;
    }

    public String getUvRecordTopic() {
        return uvRecordTopic;
    }

    public void setUvRecordTopic(String uvRecordTopic) {
        this.uvRecordTopic = uvRecordTopic;
    }

    public String getBlogPublishedTopic() {
        return blogPublishedTopic;
    }

    public void setBlogPublishedTopic(String blogPublishedTopic) {
        this.blogPublishedTopic = blogPublishedTopic;
    }

    public String getNormalVoucherOrderTopic() {
        return normalVoucherOrderTopic;
    }

    public void setNormalVoucherOrderTopic(String normalVoucherOrderTopic) {
        this.normalVoucherOrderTopic = normalVoucherOrderTopic;
    }
}
