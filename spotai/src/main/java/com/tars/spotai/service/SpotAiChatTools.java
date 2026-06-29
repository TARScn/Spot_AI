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
import java.util.Objects;
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

    @Tool(description = "Search for shops by keyword. Return JSON with id, name, area, avgPrice, score, shopUrl. Use shopUrl as markdown link target.")
    public String searchShop(@ToolParam(description = "keyword") String keyword) {
        if (keyword == null || keyword.isBlank()) return "[]";
        try {
            Result<List<Shop>> r = shopService.search(keyword.trim());
            if (!r.isSuccess() || r.getData() == null || r.getData().isEmpty()) return "[]";
            List<Map<String,Object>> shops = r.getData().stream().limit(8)
                    .map(s -> shopMap(s, null))
                    .collect(Collectors.toList());
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
            m.put("id", s.getId()); m.put("name", s.getName()); m.put("area", s.getArea()); m.put("address", s.getAddress());
            m.put("shopUrl", shopUrl(s.getId()));
            if (s.getAvgPrice() != null) m.put("avgPrice", s.getAvgPrice());
            if (s.getScore() != null) m.put("score", s.getScore());
            if (s.getOpenHours() != null) m.put("openHours", s.getOpenHours());
            if (s.getComments() != null) m.put("comments", s.getComments());
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) { return "{\"error\":\"query failed\"}"; }
    }

    @Tool(description = "Recommend shops by per-person budget range, keyword, area, scene or taste. Return JSON with id, name, avgPrice, score, reason, shopUrl. When user says about/around 50 yuan, set minPrice around 40 and maxPrice around 60.")
    public String recommendShops(
            @ToolParam(description = "minimum budget per person yuan, 0 for no lower bound") long minPrice,
            @ToolParam(description = "maximum budget per person yuan, 0 for no upper bound") long maxPrice,
            @ToolParam(description = "keyword, cuisine, scene, or taste; empty for no keyword") String keyword,
            @ToolParam(description = "area or business district; empty for no area limit") String area,
            @ToolParam(description = "maximum result count, default 5, max 10") int limit) {
        try {
            List<Shop> all = loadRecommendationCandidates(keyword, area);
            if (all.isEmpty()) return "[]";
            String keywordText = normalizeText(keyword);
            String areaText = normalizeText(area);
            int safeLimit = limit <= 0 ? 5 : Math.min(limit, 10);
            List<Shop> filtered = all.stream()
                    .filter(s -> priceMatches(s, minPrice, maxPrice))
                    .filter(s -> areaText.isEmpty() || containsIgnoreCase(s.getArea(), areaText) || containsIgnoreCase(s.getAddress(), areaText))
                    .filter(s -> keywordText.isEmpty()
                            || containsIgnoreCase(s.getName(), keywordText)
                            || containsIgnoreCase(s.getArea(), keywordText)
                            || containsIgnoreCase(s.getAddress(), keywordText))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());
            filtered.sort(Comparator
                    .comparing((Shop s) -> priceDistance(s, minPrice, maxPrice))
                    .thenComparing(Shop::getScore, Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(Shop::getComments, Comparator.nullsLast(Comparator.reverseOrder())));
            if (filtered.size() > safeLimit) filtered = filtered.subList(0, safeLimit);
            List<Map<String,Object>> shops = filtered.stream()
                    .map(s -> shopMap(s, recommendationReason(s, minPrice, maxPrice, keyword, area)))
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(shops);
        } catch (Exception e) { return "[]"; }
    }

    public String recommendShops(long maxPrice, String keyword) {
        return recommendShops(0, maxPrice, keyword, "", 5);
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

    private List<Shop> loadRecommendationCandidates(String keyword, String area) {
        String searchText = String.join(" ", normalizeText(keyword), normalizeText(area)).trim();
        if (!searchText.isBlank()) {
            Result<List<Shop>> r = shopService.search(searchText);
            if (r.isSuccess() && r.getData() != null && !r.getData().isEmpty()) {
                return r.getData();
            }
        }
        return shopRepository.findAll();
    }

    private boolean priceMatches(Shop shop, long minPrice, long maxPrice) {
        if (shop == null || shop.getAvgPrice() == null) {
            return false;
        }
        long price = shop.getAvgPrice();
        return (minPrice <= 0 || price >= minPrice) && (maxPrice <= 0 || price <= maxPrice);
    }

    private long priceDistance(Shop shop, long minPrice, long maxPrice) {
        if (shop == null || shop.getAvgPrice() == null) {
            return Long.MAX_VALUE;
        }
        long price = shop.getAvgPrice();
        if (minPrice > 0 && maxPrice > 0) {
            return Math.abs(price - ((minPrice + maxPrice) / 2));
        }
        if (maxPrice > 0) {
            return Math.abs(maxPrice - price);
        }
        if (minPrice > 0) {
            return Math.abs(price - minPrice);
        }
        return 0;
    }

    private Map<String, Object> shopMap(Shop shop, String reason) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", shop.getId());
        m.put("name", shop.getName());
        m.put("shopUrl", shopUrl(shop.getId()));
        if (shop.getArea() != null) m.put("area", shop.getArea());
        if (shop.getAddress() != null) m.put("address", shop.getAddress());
        if (shop.getAvgPrice() != null) m.put("avgPrice", shop.getAvgPrice());
        if (shop.getScore() != null) m.put("score", shop.getScore());
        if (shop.getComments() != null) m.put("comments", shop.getComments());
        if (reason != null && !reason.isBlank()) m.put("reason", reason);
        return m;
    }

    private String recommendationReason(Shop shop, long minPrice, long maxPrice, String keyword, String area) {
        StringBuilder reason = new StringBuilder();
        if (shop.getAvgPrice() != null) {
            reason.append("人均约").append(shop.getAvgPrice()).append("元");
            if (minPrice > 0 || maxPrice > 0) {
                reason.append("，符合预算");
            }
        }
        if (shop.getScore() != null) {
            if (!reason.isEmpty()) reason.append("；");
            reason.append("评分").append(String.format("%.1f", shop.getScore() / 10.0));
        }
        if (!normalizeText(area).isEmpty() && (containsIgnoreCase(shop.getArea(), area) || containsIgnoreCase(shop.getAddress(), area))) {
            if (!reason.isEmpty()) reason.append("；");
            reason.append("位置匹配").append(area.trim());
        }
        if (!normalizeText(keyword).isEmpty() && (containsIgnoreCase(shop.getName(), keyword) || containsIgnoreCase(shop.getAddress(), keyword))) {
            if (!reason.isEmpty()) reason.append("；");
            reason.append("关键词匹配").append(keyword.trim());
        }
        return reason.toString();
    }

    private String shopUrl(Long shopId) {
        return shopId == null ? "" : "spotai://shop/" + shopId;
    }

    private String normalizeText(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean containsIgnoreCase(String text, String keyword) {
        if (text == null || keyword == null || keyword.isBlank()) {
            return false;
        }
        return text.toLowerCase().contains(keyword.trim().toLowerCase());
    }
}
