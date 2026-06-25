package com.tars.spotai.service;

public interface ShopGuideAgent {
    String AGENT_NAME = "ShopGuideAgent";

    String buildContext(Long shopId);
}
