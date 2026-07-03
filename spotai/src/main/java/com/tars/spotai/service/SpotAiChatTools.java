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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

@Component
public class SpotAiChatTools {
    private static final int MAX_REVIEW_SUMMARY_LOOKUPS_PER_RECOMMENDATION = 8;

    private final ShopService shopService;
    private final ShopRepository shopRepository;
    private final VoucherRepository voucherRepository;
    private final VoucherService voucherService;
    private final ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider;
    private final ObjectMapper objectMapper;
    private AiToolCallLogger toolCallLogger = AiToolCallLogger.NOOP;

    public SpotAiChatTools(ShopService shopService, ShopRepository shopRepository, VoucherRepository voucherRepository, VoucherService voucherService, ObjectProvider<ReviewSummaryService> reviewSummaryServiceProvider, ObjectMapper objectMapper) {
        this.shopService = shopService;
        this.shopRepository = shopRepository;
        this.voucherRepository = voucherRepository;
        this.voucherService = voucherService;
        this.reviewSummaryServiceProvider = reviewSummaryServiceProvider;
        this.objectMapper = objectMapper;
    }

    @Autowired(required = false)
    public void setToolCallLogger(AiToolCallLogger toolCallLogger) {
        this.toolCallLogger = toolCallLogger == null ? AiToolCallLogger.NOOP : toolCallLogger;
    }

    @Tool(description = "Search for shops by keyword. Return JSON with id, name, area, avgPrice, score, shopUrl, markdownLink. Use markdownLink in the final answer.")
    public String searchShop(@ToolParam(description = "keyword") String keyword) {
        return executeTool("searchShop", Map.of("keyword", keyword == null ? "" : keyword), "shop", null, "[]", () -> {
            if (keyword == null || keyword.isBlank()) return "[]";
            Result<List<Shop>> r = shopService.search(keyword.trim());
            if (!r.isSuccess() || r.getData() == null || r.getData().isEmpty()) return "[]";
            List<Map<String,Object>> shops = r.getData().stream().limit(8)
                    .map(s -> shopMap(s, null))
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(shops);
        });
    }

    @Tool(description = "Get detailed shop info including name area address avgPrice score")
    public String queryShopDetail(@ToolParam(description = "shop id") long shopId) {
        return executeTool("queryShopDetail", Map.of("shopId", shopId), "shop", shopId, "{\"error\":\"query failed\"}", () -> {
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
        });
    }

    @Tool(description = "Recommend shops by per-person budget range, keyword, area, scene or taste. Return JSON with id, name, avgPrice, score, reason, reasons, shopUrl, markdownLink, answerHint. Use markdownLink or answerHint in the final answer. When user says about/around 50 yuan, set minPrice around 40 and maxPrice around 60.")
    public String recommendShops(
            @ToolParam(description = "minimum budget per person yuan, 0 for no lower bound") long minPrice,
            @ToolParam(description = "maximum budget per person yuan, 0 for no upper bound") long maxPrice,
            @ToolParam(description = "keyword, cuisine, scene, or taste; empty for no keyword") String keyword,
            @ToolParam(description = "area or business district; empty for no area limit") String area,
            @ToolParam(description = "maximum result count, default 5, max 10") int limit) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("minPrice", minPrice);
        input.put("maxPrice", maxPrice);
        input.put("keyword", keyword == null ? "" : keyword);
        input.put("area", area == null ? "" : area);
        input.put("limit", limit);
        return executeTool("recommendShops", input, "shop", null, "[]", () -> {
            List<Shop> all = loadRecommendationCandidates(keyword, area);
            if (all.isEmpty()) return "[]";
            String keywordText = normalizeText(keyword);
            String areaText = normalizeText(area);
            int safeLimit = limit <= 0 ? 5 : Math.min(limit, 10);
            List<String> keywordTokens = keywordTokens(keywordText);
            AtomicInteger reviewSummaryLookupBudget = new AtomicInteger(MAX_REVIEW_SUMMARY_LOOKUPS_PER_RECOMMENDATION);
            List<ShopRecommendation> recommendations = all.stream()
                    .filter(s -> priceMatches(s, minPrice, maxPrice))
                    .filter(s -> areaText.isEmpty() || containsIgnoreCase(s.getArea(), areaText) || containsIgnoreCase(s.getAddress(), areaText))
                    .filter(Objects::nonNull)
                    .map(s -> buildRecommendation(s, minPrice, maxPrice, keywordText, keywordTokens, areaText, reviewSummaryLookupBudget))
                    .filter(r -> keywordTokens.isEmpty() || r.keywordMatched())
                    .collect(Collectors.toList());
            Comparator<ShopRecommendation> comparator = Comparator
                    .comparingInt(ShopRecommendation::matchScore).reversed()
                    .thenComparingLong(r -> priceDistance(r.shop(), minPrice, maxPrice))
                    .thenComparing(r -> r.shop().getScore(), Comparator.nullsLast(Comparator.reverseOrder()))
                    .thenComparing(r -> r.shop().getComments(), Comparator.nullsLast(Comparator.reverseOrder()));
            if (keywordTokens.isEmpty() && areaText.isEmpty() && (minPrice > 0 || maxPrice > 0)) {
                comparator = Comparator
                        .comparingLong((ShopRecommendation r) -> priceDistance(r.shop(), minPrice, maxPrice))
                        .thenComparing(r -> r.shop().getScore(), Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(r -> r.shop().getComments(), Comparator.nullsLast(Comparator.reverseOrder()));
            }
            recommendations.sort(comparator);
            if (recommendations.size() > safeLimit) recommendations = recommendations.subList(0, safeLimit);
            List<Map<String,Object>> shops = recommendations.stream()
                    .map(this::recommendationMap)
                    .collect(Collectors.toList());
            return objectMapper.writeValueAsString(shops);
        });
    }

    public String recommendShops(long maxPrice, String keyword) {
        return recommendShops(0, maxPrice, keyword, "", 5);
    }

    @Tool(description = "Get AI summary of shop reviews")
    public String queryReviewSummary(@ToolParam(description = "shop id") long shopId) {
        return executeTool("queryReviewSummary", Map.of("shopId", shopId), "shop", shopId, "{\"status\":\"ERROR\"}", () -> {
            ReviewSummaryService svc = reviewSummaryServiceProvider.getIfAvailable();
            if (svc == null) return "{\"status\":\"UNAVAILABLE\"}";
            Result<ReviewSummaryDTO> r = svc.querySummary(shopId);
            ReviewSummaryDTO s = r.getData();
            if (!r.isSuccess() || s == null || !ReviewSummaryDTO.STATUS_READY.equals(s.getStatus())) return "{\"status\":\"UNAVAILABLE\"}";
            return objectMapper.writeValueAsString(s);
        });
    }

    @Tool(description = "Get available coupons for a shop")
    public String queryCoupons(@ToolParam(description = "shop id") long shopId) {
        return executeTool("queryCoupons", Map.of("shopId", shopId), "shop", shopId, "[]", () -> {
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
        });
    }

    private String executeTool(String toolName, Object input, String targetType, Long targetId,
                               String fallback, ToolOperation operation) {
        return executeTool(toolName, "low", input, targetType, targetId, fallback, operation);
    }

    private String executeTool(String toolName, String riskLevel, Object input, String targetType, Long targetId,
                               String fallback, ToolOperation operation) {
        if (isConfirmRequired(riskLevel)) {
            String confirmToken = UUID.randomUUID().toString();
            logToolCall(toolName, riskLevel, input, targetType, targetId, "{\"status\":\"pending\"}", "pending", null, confirmToken);
            return "{\"status\":\"CONFIRM_REQUIRED\",\"toolName\":\"" + toolName + "\",\"confirmToken\":\"" + confirmToken + "\"}";
        }
        String output = fallback;
        String status = "success";
        String error = null;
        try {
            output = operation.get();
            return output;
        } catch (Exception e) {
            status = "failed";
            error = e.getMessage();
            return fallback;
        } finally {
            logToolCall(toolName, riskLevel, input, targetType, resolveTargetId(targetId, output), output, status, error, null);
        }
    }

    private void logToolCall(String toolName, String riskLevel, Object input, String targetType, Long targetId,
                             String output, String status, String error, String confirmToken) {
        try {
            toolCallLogger.log(new AiToolCallLogger.ToolCallLogCommand(
                    toolName,
                    riskLevel,
                    targetType,
                    targetId,
                    input,
                    output,
                    status,
                    error,
                    confirmToken));
        } catch (Exception ignored) {
        }
    }

    private boolean isConfirmRequired(String riskLevel) {
        return "medium".equalsIgnoreCase(riskLevel) || "high".equalsIgnoreCase(riskLevel);
    }

    @Tool(description = "Claim a coupon/voucher for the currently logged-in user. Medium risk - requires user confirmation.")
    public String claimCoupon(@ToolParam(description = "voucher id") long voucherId) {
        return executeTool("claimCoupon", "medium", Map.of("voucherId", voucherId), "coupon", voucherId,
                "{\"status\":\"ERROR\",\"message\":\"领券失败\"}", () ->
                executeClaimCoupon(voucherId));
    }

    private String executeClaimCoupon(long voucherId) {
        try {
            com.tars.spotai.dto.Result<Long> result = voucherService.claimVoucher(voucherId);
            if (result != null && result.getData() != null) {
                return "{\"status\":\"SUCCESS\",\"orderId\":" + result.getData() + "}";
            }
            String msg = result == null ? "系统异常" : result.getErrorMsg();
            return "{\"status\":\"FAILED\",\"message\":\"" + escapeJsonString(msg) + "\"}";
        } catch (Exception e) {
            return "{\"status\":\"ERROR\",\"message\":\"" + escapeJsonString(e.getMessage()) + "\"}";
        }
    }

    private String escapeJsonString(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private Long resolveTargetId(Long explicitTargetId, String output) {
        if (explicitTargetId != null) {
            return explicitTargetId;
        }
        try {
            var node = objectMapper.readTree(output);
            if (node.isArray() && !node.isEmpty() && node.get(0).has("id")) {
                return node.get(0).get("id").asLong();
            }
            if (node.isObject() && node.has("id")) {
                return node.get("id").asLong();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private List<Shop> loadRecommendationCandidates(String keyword, String area) {
        String searchText = String.join(" ", normalizeText(keyword), normalizeText(area)).trim();
        if (!searchText.isBlank()) {
            Result<List<Shop>> r = shopService.search(searchText);
            if (r != null && r.isSuccess() && r.getData() != null && !r.getData().isEmpty()) {
                return r.getData();
            }
        }
        return shopRepository.findAll();
    }

    private ShopRecommendation buildRecommendation(Shop shop, long minPrice, long maxPrice, String keyword,
                                                   List<String> keywordTokens, String area,
                                                   AtomicInteger reviewSummaryLookupBudget) {
        List<String> reasons = new ArrayList<>();
        List<Voucher> coupons = loadCoupons(shop.getId());
        ReviewSummaryDTO reviewSummary = loadReviewSummary(shop.getId(), reviewSummaryLookupBudget);
        int score = 0;

        if (shop.getAvgPrice() != null) {
            int priceScore = priceScore(shop, minPrice, maxPrice);
            score += priceScore;
            String reason = "人均约" + shop.getAvgPrice() + "元";
            if (minPrice > 0 || maxPrice > 0) {
                reason += "，符合预算";
            }
            reasons.add(reason);
        }

        if (shop.getScore() != null) {
            score += Math.min(25, Math.max(0, shop.getScore() / 4));
            reasons.add("评分" + String.format("%.1f", shop.getScore() / 10.0));
        }

        if (shop.getComments() != null && shop.getComments() > 0) {
            score += Math.min(8, shop.getComments() / 20);
            reasons.add("已有" + shop.getComments() + "条评价");
        }

        if (shop.getSold() != null && shop.getSold() > 0) {
            score += Math.min(6, shop.getSold() / 50);
        }

        boolean areaMatched = !area.isEmpty()
                && (containsIgnoreCase(shop.getArea(), area) || containsIgnoreCase(shop.getAddress(), area));
        if (areaMatched) {
            score += 10;
            reasons.add("位置匹配" + area);
        }

        MatchResult textMatch = matchTokens(keywordTokens, shopText(shop));
        MatchResult reviewMatch = matchTokens(keywordTokens, reviewSummaryText(reviewSummary));
        MatchResult couponMatch = matchTokens(keywordTokens, couponText(coupons));
        if (textMatch.matched()) {
            score += Math.min(18, 8 + textMatch.matchedCount() * 5);
            reasons.add("店铺信息匹配：" + String.join("、", textMatch.matchedTokens()));
        }
        if (reviewMatch.matched()) {
            score += Math.min(20, 10 + reviewMatch.matchedCount() * 5);
            reasons.add("评价场景匹配：" + String.join("、", reviewMatch.matchedTokens()));
        }
        if (couponMatch.matched()) {
            score += Math.min(12, 6 + couponMatch.matchedCount() * 4);
            reasons.add("优惠信息匹配：" + String.join("、", couponMatch.matchedTokens()));
        }
        if (!coupons.isEmpty()) {
            score += 10;
            reasons.add("当前有可用优惠");
        }

        score = Math.min(score, 100);
        boolean keywordMatched = keywordTokens.isEmpty()
                || textMatch.matched()
                || reviewMatch.matched()
                || couponMatch.matched()
                || (!coupons.isEmpty() && containsCouponIntent(keyword));
        return new ShopRecommendation(shop, score, reasons, coupons, reviewSummary, keywordMatched);
    }

    private Map<String, Object> recommendationMap(ShopRecommendation recommendation) {
        Shop shop = recommendation.shop();
        Map<String, Object> m = shopMap(shop, String.join("；", recommendation.reasons()));
        m.put("answerHint", answerHint(shop, recommendation.reasons()));
        m.put("matchScore", recommendation.matchScore());
        m.put("reasons", recommendation.reasons());
        m.put("hasCoupon", !recommendation.coupons().isEmpty());
        if (!recommendation.coupons().isEmpty()) {
            m.put("couponTitles", recommendation.coupons().stream()
                    .map(Voucher::getTitle)
                    .filter(Objects::nonNull)
                    .limit(3)
                    .collect(Collectors.toList()));
        }
        ReviewSummaryDTO summary = recommendation.reviewSummary();
        if (summary != null) {
            if (summary.getSummary() != null) m.put("reviewSummary", summary.getSummary());
            if (summary.getScenes() != null && !summary.getScenes().isEmpty()) m.put("reviewScenes", summary.getScenes());
            if (summary.getHighlights() != null && !summary.getHighlights().isEmpty()) m.put("reviewHighlights", summary.getHighlights());
        }
        return m;
    }

    private List<Voucher> loadCoupons(Long shopId) {
        if (shopId == null) {
            return Collections.emptyList();
        }
        try {
            List<Voucher> vouchers = voucherRepository.findActiveByShopId(shopId, LocalDateTime.now(), 3);
            return vouchers == null ? Collections.emptyList() : vouchers;
        } catch (Exception ignored) {
            return Collections.emptyList();
        }
    }

    private ReviewSummaryDTO loadReviewSummary(Long shopId, AtomicInteger lookupBudget) {
        if (shopId == null) {
            return null;
        }
        try {
            ReviewSummaryService svc = reviewSummaryServiceProvider == null ? null : reviewSummaryServiceProvider.getIfAvailable();
            if (svc == null) {
                return null;
            }
            java.util.Optional<ReviewSummaryDTO> cached = svc.queryCachedSummary(shopId);
            if (cached != null && cached.isPresent()
                    && ReviewSummaryDTO.STATUS_READY.equals(cached.get().getStatus())) {
                return cached.get();
            }
            if (lookupBudget == null || lookupBudget.getAndDecrement() <= 0) {
                return null;
            }
            Result<ReviewSummaryDTO> result = svc.querySummary(shopId);
            ReviewSummaryDTO summary = result == null ? null : result.getData();
            if (result == null || !result.isSuccess() || summary == null
                    || !ReviewSummaryDTO.STATUS_READY.equals(summary.getStatus())) {
                return null;
            }
            return summary;
        } catch (Exception ignored) {
            return null;
        }
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

    private int priceScore(Shop shop, long minPrice, long maxPrice) {
        if (shop == null || shop.getAvgPrice() == null) {
            return 0;
        }
        if (minPrice <= 0 && maxPrice <= 0) {
            return 12;
        }
        long distance = priceDistance(shop, minPrice, maxPrice);
        long width = minPrice > 0 && maxPrice > 0 ? Math.max(1, maxPrice - minPrice) : Math.max(20, maxPrice);
        return Math.max(12, 35 - (int) Math.min(23, distance * 23 / Math.max(1, width)));
    }

    private Map<String, Object> shopMap(Shop shop, String reason) {
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id", shop.getId());
        m.put("name", shop.getName());
        m.put("shopUrl", shopUrl(shop.getId()));
        m.put("markdownLink", markdownLink(shop));
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

    private String answerHint(Shop shop, List<String> reasons) {
        StringBuilder hint = new StringBuilder(markdownLink(shop));
        if (shop.getAvgPrice() != null) {
            hint.append(" avgPrice=").append(shop.getAvgPrice());
        }
        if (shop.getScore() != null) {
            hint.append(" score=").append(String.format("%.1f", shop.getScore() / 10.0));
        }
        if (reasons != null && !reasons.isEmpty()) {
            hint.append(" reason=").append(String.join("; ", reasons));
        }
        return hint.toString();
    }

    private String markdownLink(Shop shop) {
        if (shop == null) {
            return "";
        }
        String name = shop.getName() == null || shop.getName().isBlank() ? "shop " + shop.getId() : shop.getName();
        return "[" + name + "](" + shopUrl(shop.getId()) + ")";
    }

    private List<String> keywordTokens(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return List.of();
        }
        Set<String> tokens = new HashSet<>();
        for (String segment : keyword.trim().split("[\\s,，。；;、/|]+")) {
            String value = segment.trim().toLowerCase();
            if (value.isEmpty() || isRecommendationStopWord(value)) {
                continue;
            }
            tokens.add(value);
        }
        return tokens.stream().limit(8).collect(Collectors.toList());
    }

    private boolean isRecommendationStopWord(String value) {
        return Set.of("推荐", "附近", "几家", "一家", "店", "商家", "人均", "左右", "以内",
                "以下", "以上", "about", "around", "near", "shop", "restaurant").contains(value);
    }

    private MatchResult matchTokens(List<String> tokens, String text) {
        if (tokens == null || tokens.isEmpty() || text == null || text.isBlank()) {
            return new MatchResult(List.of());
        }
        String lower = text.toLowerCase();
        List<String> matched = tokens.stream()
                .filter(token -> lower.contains(token.toLowerCase()))
                .distinct()
                .collect(Collectors.toList());
        return new MatchResult(matched);
    }

    private boolean containsCouponIntent(String keyword) {
        String text = normalizeText(keyword).toLowerCase();
        return containsAnyIgnoreCase(text, "优惠", "券", "折扣", "coupon", "discount", "deal");
    }

    private String shopText(Shop shop) {
        if (shop == null) {
            return "";
        }
        return String.join(" ",
                normalizeText(shop.getName()),
                normalizeText(shop.getArea()),
                normalizeText(shop.getAddress()),
                normalizeText(shop.getOpenHours()));
    }

    private String couponText(List<Voucher> coupons) {
        if (coupons == null || coupons.isEmpty()) {
            return "";
        }
        return coupons.stream()
                .map(v -> String.join(" ",
                        normalizeText(v.getTitle()),
                        normalizeText(v.getSubTitle()),
                        normalizeText(v.getRules())))
                .collect(Collectors.joining(" "));
    }

    private String reviewSummaryText(ReviewSummaryDTO summary) {
        if (summary == null) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        parts.add(normalizeText(summary.getSummary()));
        if (summary.getHighlights() != null) parts.addAll(summary.getHighlights());
        if (summary.getWeaknesses() != null) parts.addAll(summary.getWeaknesses());
        if (summary.getScenes() != null) parts.addAll(summary.getScenes());
        return parts.stream().filter(Objects::nonNull).collect(Collectors.joining(" "));
    }

    private boolean containsAnyIgnoreCase(String text, String... keywords) {
        if (text == null) {
            return false;
        }
        String lower = text.toLowerCase();
        for (String keyword : keywords) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
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

    private record ShopRecommendation(Shop shop, int matchScore, List<String> reasons, List<Voucher> coupons,
                                      ReviewSummaryDTO reviewSummary, boolean keywordMatched) {
    }

    private record MatchResult(List<String> matchedTokens) {
        boolean matched() {
            return matchedTokens != null && !matchedTokens.isEmpty();
        }

        int matchedCount() {
            return matchedTokens == null ? 0 : matchedTokens.size();
        }
    }

    @FunctionalInterface
    private interface ToolOperation {
        String get() throws Exception;
    }
}
