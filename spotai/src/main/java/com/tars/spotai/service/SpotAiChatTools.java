package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.Result;
import com.tars.spotai.dto.ReviewSummaryDTO;
import com.tars.spotai.entity.Shop;
import com.tars.spotai.entity.Voucher;
import com.tars.spotai.repository.ShopRepository;
import com.tars.spotai.repository.VoucherRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class SpotAiChatTools {
    private final ShopService shopService;
    private final ShopRepository shopRepository;
    private final VoucherRepository voucherRepository;
    private final ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider;
    private final ObjectMapper objectMapper;

    public SpotAiChatTools(ShopService shopService, ShopRepository shopRepository, VoucherRepository voucherRepository, ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider, ObjectMapper objectMapper) {
        this.shopService = shopService;
        this.shopRepository = shopRepository;
        this.voucherRepository = voucherRepository;
        this.reviewSummaryServiceProvider = reviewSummaryServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Tool(description = "Search for shops by keyword")
    public String searchShop(@ToolParam(description = "keyword") String keyword) {
        if (keyword == null || keyword.isBlank()) return "[]";
        try {
            Result<List<Shop>> r = shopService.search(keyword.trim());
            if (!r.isSuccess() || r.getData() == null || r.getData().isEmpty()) return "[]";
            List<Map<String,Object>> shops = r.getData().stream().limit(8).map(s -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", s.getId()); m.put("name", s.getName()); m.put("area", s.getArea());
                if (s.getAvgPrice() != null) m.put("avgPrice", s.getAvgPrice());
                if (s.getScore() != null) m.put("score", s.getScore());
                return m;
            }).collect(Collectors.toList());
            return objectMapper.writeValueAsString(shops);
        } catch (Exception e) { return "[]"; }
    }

    @Tool(description = "Get detailed shop info including name area address avgPrice score")
    public String queryShopDetail(@ToolParam(description = "shop id") long shopId) {
        try {
            Result<Shop> r = shopService.queryById(shopId);
            if (!r.isSuccess() || r.getData() == null) return "{\"error\":\"not found\"}";
            Shop s = r.getData();
            Map<String,Object> m = new LinkedHashMap<>();
            m.put("name", s.getName()); m.put("area", s.getArea()); m.put("address", s.getAddress());
            if (s.getAvgPrice() != null) m.put("avgPrice", s.getAvgPrice());
            if (s.getScore() != null) m.put("score", s.getScore());
            if (s.getOpenHours() != null) m.put("openHours", s.getOpenHours());
            if (s.getComments() != null) m.put("comments", s.getComments());
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) { return "{\"error\":\"query failed\"}"; }
    }

    @Tool(description = "Recommend shops by max budget per person and keyword search results")
    public String recommendShops(@ToolParam(description = "max budget per person yuan, 0 for no limit") long maxPrice, @ToolParam(description = "search keyword") String keyword) {
        try {
            List<Shop> all;
            if (keyword != null && !keyword.isBlank()) {
                Result<List<Shop>> r = shopService.search(keyword.trim());
                all = (r.isSuccess() && r.getData() != null) ? r.getData() : List.of();
            } else {
                all = shopRepository.findAll();
            }
            if (all.isEmpty()) return "[]";
            List<Shop> filtered = (maxPrice > 0) ? all.stream().filter(s -> s.getAvgPrice() != null && s.getAvgPrice() <= maxPrice).collect(Collectors.toList()) : all;
            filtered.sort(Comparator.comparing(Shop::getScore, Comparator.nullsLast(Comparator.reverseOrder())));
            if (filtered.size() > 10) filtered = filtered.subList(0, 10);
            List<Map<String,Object>> shops = filtered.stream().map(s -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", s.getId()); m.put("name", s.getName()); m.put("area", s.getArea());
                if (s.getAvgPrice() != null) m.put("avgPrice", s.getAvgPrice());
                if (s.getScore() != null) m.put("score", s.getScore());
                return m;
            }).collect(Collectors.toList());
            return objectMapper.writeValueAsString(shops);
        } catch (Exception e) { return "[]"; }
    }

    @Tool(description = "Get AI summary of shop reviews")
    public String queryReviewSummary(@ToolParam(description = "shop id") long shopId) {
        try {
            ReviewSummaryService svc = reviewSummaryServiceProvider.getIfAvailable();
            if (svc == null) return "{\"status\":\"UNAVAILABLE\"}";
            Result<ReviewSummaryDTO> r = svc.querySummary(shopId);
            ReviewSummaryDTO s = r.getData();
            if (!r.isSuccess() || s == null || !ReviewSummaryDTO.STATUS_READY.equals(s.getStatus())) return "{\"status\":\"UNAVAILABLE\"}";
            return objectMapper.writeValueAsString(s);
        } catch (Exception e) { return "{\"status\":\"ERROR\"}"; }
    }

    @Tool(description = "Get available coupons for a shop")
    public String queryCoupons(@ToolParam(description = "shop id") long shopId) {
        try {
            List<Voucher> vouchers = voucherRepository.findActiveByShopId(shopId, LocalDateTime.now(), 8);
            if (vouchers.isEmpty()) return "[]";
            List<Map<String,Object>> items = vouchers.stream().map(v -> {
                Map<String,Object> m = new LinkedHashMap<>();
                m.put("id", v.getId()); m.put("title", v.getTitle());
                if (v.getSubTitle() != null) m.put("subTitle", v.getSubTitle());
                m.put("payValue", v.getPayValue()); m.put("actualValue", v.getActualValue());
                if (v.getRules() != null) m.put("rules", v.getRules());
                return m;
            }).collect(Collectors.toList());
            return objectMapper.writeValueAsString(items);
        } catch (Exception e) { return "[]"; }
    }
}