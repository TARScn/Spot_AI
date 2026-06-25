package com.tars.spotai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.entity.Shop;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpringAiShopGuideAgent implements ShopGuideAgent {
    private final ShopService shopService;
    private final ObjectMapper objectMapper;

    public SpringAiShopGuideAgent(ShopService shopService, ObjectMapper objectMapper) {
        this.shopService = shopService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String buildContext(Long shopId) {
        if (shopId == null || shopId <= 0) {
            return "";
        }
        try {
            Result<Shop> result = shopService.queryById(shopId);
            if (!result.isSuccess() || result.getData() == null) {
                return "";
            }
            Shop shop = result.getData();
            Map<String, Object> context = new LinkedHashMap<>();
            context.put("name", shop.getName());
            context.put("area", shop.getArea());
            context.put("address", shop.getAddress());
            if (shop.getAvgPrice() != null) {
                context.put("avgPrice", shop.getAvgPrice());
            }
            if (shop.getScore() != null) {
                context.put("score", shop.getScore());
            }
            if (shop.getOpenHours() != null) {
                context.put("openHours", shop.getOpenHours());
            }
            if (shop.getComments() != null) {
                context.put("comments", shop.getComments());
            }
            if (shop.getSold() != null) {
                context.put("sold", shop.getSold());
            }
            return objectMapper.writeValueAsString(context);
        } catch (Exception ignored) {
            return "";
        }
    }

    public String buildSearchContext(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return "";
        }
        try {
            Result<List<Shop>> result = shopService.search(keyword.trim());
            if (!result.isSuccess() || result.getData() == null || result.getData().isEmpty()) {
                return "";
            }
            List<Map<String, Object>> shops = result.getData().stream()
                    .sorted(Comparator.comparing(Shop::getScore, Comparator.nullsLast(Comparator.reverseOrder())))
                    .limit(6)
                    .map(shop -> {
                        Map<String, Object> item = new LinkedHashMap<>();
                        item.put("id", shop.getId());
                        item.put("name", shop.getName());
                        item.put("area", shop.getArea());
                        if (shop.getAvgPrice() != null) item.put("avgPrice", shop.getAvgPrice());
                        if (shop.getScore() != null) item.put("score", shop.getScore());
                        return item;
                    })
                    .toList();
            return objectMapper.writeValueAsString(shops);
        } catch (Exception ignored) {
            return "";
        }
    }
}
