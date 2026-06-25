package com.tars.spotai.service;

public interface CouponAgent {
    String AGENT_NAME = "CouponAgent";

    String buildContext(Long shopId);
}
