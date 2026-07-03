package com.tars.spotai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.entity.AiUserMemory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class RecommendationPreferenceResolver {
    private static final Pattern BUDGET_PATTERN =
            Pattern.compile("(?:人均|预算|消费|价格)\\s*(\\d{1,4})\\s*(?:元|块|块钱)?\\s*(左右|以内|以下|上下|附近|内)?"
                    + "|(\\d{1,4})\\s*(?:元|块|块钱)\\s*(左右|以内|以下|上下|附近|内)?");
    private static final List<String> AREA_KEYWORDS = List.of(
            "钟楼", "小寨", "高新", "曲江", "大雁塔", "长安", "雁塔", "碑林", "未央", "莲湖", "灞桥", "西咸"
    );
    private static final List<String> SHOP_KEYWORDS = List.of(
            "火锅", "烧烤", "烤肉", "串串", "面", "小吃", "咖啡", "奶茶", "甜品", "西餐", "日料", "韩餐",
            "川菜", "陕菜", "自助", "聚餐", "早餐", "夜宵"
    );

    private static final List<String> SCENE_KEYWORDS = List.of(
            "约会", "安静", "聚餐", "朋友", "亲子", "商务", "夜宵", "下午茶", "拍照", "高分", "性价比", "优惠", "折扣", "团购",
            "date", "quiet", "family", "business", "coupon", "discount", "deal"
    );

    private final ObjectMapper objectMapper;

    public RecommendationPreferenceResolver(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public boolean isRecommendationIntent(String message) {
        if (!StringUtils.hasText(message)) {
            return false;
        }
        String lower = message.toLowerCase(Locale.ROOT);
        return containsAny(lower, "推荐", "找", "附近", "哪家", "去哪", "哪里", "recommend");
    }

    public RecommendationPreference resolve(String message, List<AiUserMemory> memories) {
        long[] range = parseBudgetRange(message);
        RecommendationPreference memoryPreference = parseMemories(memories);
        if (range == null) {
            range = memoryPreference.budgetRange();
        }
        String keyword = parseKeyword(message);
        if (!StringUtils.hasText(keyword)) {
            keyword = memoryPreference.keyword();
        }
        String area = parseArea(message);
        if (!StringUtils.hasText(area)) {
            area = memoryPreference.area();
        }
        return new RecommendationPreference(range, keyword == null ? "" : keyword, area == null ? "" : area);
    }

    private long[] parseBudgetRange(String message) {
        if (!StringUtils.hasText(message)) {
            return null;
        }
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        while (matcher.find()) {
            String valueText = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
            String qualifier = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
            long value;
            try {
                value = Long.parseLong(valueText);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value <= 0) {
                continue;
            }

            if (qualifier != null && (qualifier.contains("左右")
                    || qualifier.contains("上下")
                    || qualifier.contains("附近"))) {
                long delta = Math.max(10, Math.round(value * 0.2));
                return new long[]{Math.max(1, value - delta), value + delta};
            }
            return new long[]{0, value};
        }
        return null;
    }

    private String parseArea(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        return AREA_KEYWORDS.stream()
                .filter(message::contains)
                .findFirst()
                .orElse("");
    }

    private String parseKeyword(String message) {
        if (!StringUtils.hasText(message)) {
            return "";
        }
        LinkedHashSet<String> keywords = new LinkedHashSet<>();
        SHOP_KEYWORDS.stream()
                .filter(message::contains)
                .findFirst()
                .ifPresent(keywords::add);
        SCENE_KEYWORDS.stream()
                .filter(message::contains)
                .forEach(keywords::add);
        return String.join(" ", keywords);
    }

    private RecommendationPreference parseMemories(List<AiUserMemory> memories) {
        if (memories == null || memories.isEmpty()) {
            return RecommendationPreference.empty();
        }
        long[] budget = null;
        String area = "";
        String keyword = "";
        for (AiUserMemory memory : memories) {
            if (memory == null || !StringUtils.hasText(memory.getMemoryJson())) {
                continue;
            }
            try {
                JsonNode root = objectMapper.readTree(memory.getMemoryJson());
                if (budget == null) {
                    budget = extractBudget(root);
                }
                if (!StringUtils.hasText(area)) {
                    area = firstText(root, "area", "district", "businessDistrict", "preferred_area");
                }
                if (!StringUtils.hasText(keyword)) {
                    keyword = keywordText(root);
                }
            } catch (Exception ignored) {
                String text = memory.getMemoryJson();
                if (!StringUtils.hasText(keyword)) {
                    keyword = parseKeyword(text);
                }
                if (!StringUtils.hasText(area)) {
                    area = AREA_KEYWORDS.stream().filter(text::contains).findFirst().orElse("");
                }
            }
        }
        return new RecommendationPreference(budget, keyword == null ? "" : keyword, area == null ? "" : area);
    }

    private String keywordText(JsonNode root) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        for (String key : List.of("keyword", "category", "cuisine", "taste", "preferred_category")) {
            collectTextValues(values, findValue(root, key));
        }
        collectSceneTextValues(values, findValue(root, "scene"));
        return String.join(" ", values);
    }

    private void collectSceneTextValues(LinkedHashSet<String> values, JsonNode node) {
        LinkedHashSet<String> scenes = new LinkedHashSet<>();
        collectTextValues(scenes, node);
        scenes.stream()
                .filter(this::isKnownSceneKeyword)
                .forEach(values::add);
    }

    private boolean isKnownSceneKeyword(String value) {
        if (!StringUtils.hasText(value)) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() > 3) {
            return false;
        }
        return SCENE_KEYWORDS.stream().anyMatch(keyword -> trimmed.contains(keyword) || keyword.contains(trimmed));
    }

    private void collectTextValues(LinkedHashSet<String> values, JsonNode node) {
        if (node == null || node.isNull() || values.size() >= 8) {
            return;
        }
        if (node.isTextual()) {
            String text = node.asText().trim();
            if (StringUtils.hasText(text)) {
                values.add(text);
            }
            return;
        }
        if (node.isNumber() || node.isBoolean()) {
            values.add(node.asText());
            return;
        }
        if (node.isArray() || node.isObject()) {
            for (JsonNode child : node) {
                collectTextValues(values, child);
                if (values.size() >= 8) {
                    break;
                }
            }
        }
    }

    private long[] extractBudget(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        long[] textualRange = firstBudgetRange(root, "budget", "budgetText", "priceText", "avgPriceText", "summary");
        if (textualRange != null) {
            return textualRange;
        }
        Long min = firstLong(root, "min", "minPrice", "budgetMin", "lower");
        Long max = firstLong(root, "max", "maxPrice", "budgetMax", "upper", "price", "avgPrice", "budget");
        JsonNode budget = root.get("budget_range");
        if (budget == null) {
            budget = root.get("budgetRange");
        }
        if (budget != null && budget.isObject()) {
            if (min == null) {
                min = firstLong(budget, "min", "minPrice", "budgetMin", "lower");
            }
            if (max == null) {
                max = firstLong(budget, "max", "maxPrice", "budgetMax", "upper", "price", "avgPrice", "budget");
            }
        }
        if (min == null && max == null) {
            return null;
        }
        return new long[]{min == null ? 0 : Math.max(0, min), max == null ? 0 : Math.max(0, max)};
    }

    private long[] firstBudgetRange(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = findValue(root, key);
            String text = nodeText(value);
            if (!StringUtils.hasText(text)) {
                continue;
            }
            long[] range = parseBudgetRange(text);
            if (range != null) {
                return range;
            }
        }
        return null;
    }

    private Long firstLong(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = findValue(root, key);
            if (value == null || value.isNull()) {
                continue;
            }
            if (value.isNumber()) {
                return value.asLong();
            }
            if (value.isTextual()) {
                Matcher matcher = Pattern.compile("\\d{1,4}").matcher(value.asText());
                if (matcher.find()) {
                    return Long.parseLong(matcher.group());
                }
            }
        }
        return null;
    }

    private String firstText(JsonNode root, String... keys) {
        for (String key : keys) {
            JsonNode value = findValue(root, key);
            String text = nodeText(value);
            if (StringUtils.hasText(text)) {
                return text;
            }
        }
        return "";
    }

    private JsonNode findValue(JsonNode root, String key) {
        if (root == null || key == null || root.isNull()) {
            return null;
        }
        JsonNode direct = root.get(key);
        if (direct != null) {
            return direct;
        }
        if (root.isObject() || root.isArray()) {
            for (JsonNode child : root) {
                JsonNode found = findValue(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String nodeText(JsonNode node) {
        if (node == null || node.isNull()) {
            return "";
        }
        if (node.isTextual()) {
            return node.asText().trim();
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                String text = nodeText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        if (node.isObject()) {
            for (JsonNode item : node) {
                String text = nodeText(item);
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    public record RecommendationPreference(long[] budgetRange, String keyword, String area) {
        public static RecommendationPreference empty() {
            return new RecommendationPreference(null, "", "");
        }

        public boolean hasAny() {
            return budgetRange != null || StringUtils.hasText(keyword) || StringUtils.hasText(area);
        }

        public long minPrice() {
            return budgetRange == null ? 0 : budgetRange[0];
        }

        public long maxPrice() {
            return budgetRange == null ? 0 : budgetRange[1];
        }
    }
}
