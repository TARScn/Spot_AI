package com.tars.spotai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.PreferenceMemoryCandidateDTO;
import com.tars.spotai.entity.AiUserMemory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class SpringAiPreferenceExtractorAgent implements PreferenceExtractorAgent {
    private static final int MAX_MESSAGE_CHARS = 1200;
    private static final int MAX_VALUE_CHARS = 600;
    private static final Pattern BUDGET_PATTERN = Pattern.compile(
            "(?:人均|预算|消费|价格)\\s*(\\d{1,4})\\s*(?:元|块|块钱)?\\s*(左右|上下|附近|以内|以下)?"
                    + "|(\\d{1,4})\\s*(?:元|块|块钱)\\s*(左右|上下|附近|以内|以下)?");
    private static final List<String> AREA_KEYWORDS = List.of(
            "钟楼", "小寨", "高新", "曲江", "大雁塔", "长安", "雁塔", "碑林", "未央", "莲湖", "灞桥", "西咸");
    private static final List<String> SHOP_KEYWORDS = List.of(
            "火锅", "烧烤", "烤肉", "串串", "面", "小吃", "咖啡", "奶茶", "甜品", "西餐", "日料", "韩餐",
            "川菜", "陕菜", "自助", "聚餐", "早餐", "夜宵", "KTV", "电影", "酒店");
    private static final List<String> MEMORY_DELETE_KEYWORDS = List.of(
            "删除", "忘记", "别记", "不要记", "清除", "forget", "delete");
    private static final String SYSTEM_PROMPT = """
            你是 Spot AI 的 PreferenceExtractorAgent，只负责从用户消息中抽取可长期复用的本地生活偏好。
            只允许抽取餐饮/本地生活相关的稳定偏好，例如口味、环境、预算、常去区域、排队禁忌、优惠偏好。
            不要保存一次性任务、闲聊、隐私敏感信息、手机号、身份证、密码、令牌、地址门牌号。
            只返回 JSON，不要解释。JSON 格式：
            {"memories":[{"memoryKey":"dining.preference.taste","memoryType":"preference","value":{},"confidence":0.95,"action":"UPSERT"}]}
            memoryKey 只能使用：
            dining.preference.taste, dining.preference.environment, dining.preference.budget,
            dining.preference.area, dining.preference.scene, dining.preference.discount,
            dining.avoid.keyword
            action 只能是 UPSERT 或 DELETE。没有可保存偏好时返回 {"memories":[]}。
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiPreferenceExtractorAgent(@Qualifier("openAiChatModel") ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatClient = ChatClient.create(chatModel);
        this.objectMapper = objectMapper;
    }

    @Override
    public List<PreferenceMemoryCandidateDTO> extract(Long userId, String latestUserMessage, List<AiUserMemory> existingMemories) {
        if (userId == null || !StringUtils.hasText(latestUserMessage)) {
            return List.of();
        }
        String message = latestUserMessage.strip();
        if (message.length() > MAX_MESSAGE_CHARS) {
            message = message.substring(0, MAX_MESSAGE_CHARS);
        }
        String content = "";
        try {
            content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildPrompt(userId, message, existingMemories))
                    .call()
                    .content();
        } catch (Exception ignored) {
            // Rule-based extraction below keeps key recommendation preferences available
            // when the LLM fails, times out, or returns non-JSON content.
        }
        List<PreferenceMemoryCandidateDTO> candidates = new ArrayList<>(parseCandidates(content));
        addRuleBasedCandidates(message, candidates);
        return candidates;
    }

    private String buildPrompt(Long userId, String latestUserMessage, List<AiUserMemory> existingMemories) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("userId: ").append(userId).append('\n');
        if (existingMemories != null && !existingMemories.isEmpty()) {
            prompt.append("已有记忆：\n");
            existingMemories.stream().limit(20).forEach(memory -> prompt
                    .append("- ")
                    .append(memory.getMemoryKey())
                    .append(": ")
                    .append(memory.getMemoryJson())
                    .append('\n'));
        }
        prompt.append("用户最新消息：").append(latestUserMessage);
        return prompt.toString();
    }

    List<PreferenceMemoryCandidateDTO> parseCandidates(String content) {
        if (!StringUtils.hasText(content)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            JsonNode memories = root.path("memories");
            if (!memories.isArray()) {
                return List.of();
            }
            List<PreferenceMemoryCandidateDTO> candidates = new ArrayList<>();
            for (JsonNode node : memories) {
                PreferenceMemoryCandidateDTO candidate = toCandidate(node);
                if (candidate != null) {
                    candidates.add(candidate);
                }
            }
            return candidates;
        } catch (Exception ignored) {
            return List.of();
        }
    }

    private PreferenceMemoryCandidateDTO toCandidate(JsonNode node) {
        String key = text(node, "memoryKey");
        String type = text(node, "memoryType");
        String action = normalizeAction(text(node, "action"));
        double confidence = node.path("confidence").asDouble(0);
        JsonNode value = node.path("value");
        if (!isAllowedKey(key) || !StringUtils.hasText(type) || confidence < 0.7) {
            return null;
        }
        if (!"DELETE".equals(action) && (value.isMissingNode() || value.isNull())) {
            return null;
        }
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey(key);
        candidate.setMemoryType(type.length() > 32 ? type.substring(0, 32) : type);
        candidate.setConfidence(Math.min(1.0, Math.max(0.0, confidence)));
        candidate.setAction(action);
        candidate.setValue("DELETE".equals(action) ? java.util.Map.of() : toSafeValue(value));
        return candidate;
    }

    private Object toSafeValue(JsonNode value) {
        String json = value.toString();
        if (json.length() > MAX_VALUE_CHARS) {
            json = json.substring(0, MAX_VALUE_CHARS);
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (Exception ignored) {
            return json;
        }
    }

    private String extractJson(String content) {
        String value = content.strip();
        if (value.startsWith("```")) {
            int firstNewline = value.indexOf('\n');
            int lastFence = value.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                value = value.substring(firstNewline + 1, lastFence).strip();
            }
        }
        int start = value.indexOf('{');
        int end = value.lastIndexOf('}');
        if (start >= 0 && end > start) {
            return value.substring(start, end + 1);
        }
        return value;
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText().strip() : "";
    }

    private String normalizeAction(String action) {
        String normalized = StringUtils.hasText(action) ? action.toUpperCase(Locale.ROOT) : "UPSERT";
        return "DELETE".equals(normalized) ? "DELETE" : "UPSERT";
    }

    private boolean isAllowedKey(String key) {
        return switch (key) {
            case "dining.preference.taste",
                 "dining.preference.environment",
                 "dining.preference.budget",
                 "dining.preference.area",
                 "dining.preference.scene",
                 "dining.preference.discount",
                 "dining.avoid.keyword" -> true;
            default -> false;
        };
    }

    private void addRuleBasedCandidates(String message, List<PreferenceMemoryCandidateDTO> candidates) {
        if (!StringUtils.hasText(message) || containsAny(message, MEMORY_DELETE_KEYWORDS)) {
            return;
        }
        Set<String> existingKeys = new HashSet<>();
        for (PreferenceMemoryCandidateDTO candidate : candidates) {
            if (candidate != null && StringUtils.hasText(candidate.getMemoryKey())) {
                existingKeys.add(candidate.getMemoryKey());
            }
        }

        if (!existingKeys.contains("dining.preference.budget")) {
            long[] budget = parseBudgetRange(message);
            if (budget != null) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("min", budget[0]);
                value.put("max", budget[1]);
                value.put("source", "rule");
                candidates.add(candidate("dining.preference.budget", value, 0.82));
                existingKeys.add("dining.preference.budget");
            }
        }

        if (!existingKeys.contains("dining.preference.area")) {
            String area = firstKeyword(message, AREA_KEYWORDS);
            if (StringUtils.hasText(area)) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("area", area);
                value.put("source", "rule");
                candidates.add(candidate("dining.preference.area", value, 0.78));
                existingKeys.add("dining.preference.area");
            }
        }

        if (!existingKeys.contains("dining.preference.taste")) {
            String keyword = firstKeyword(message, SHOP_KEYWORDS);
            if (StringUtils.hasText(keyword) && !message.contains("不喜欢" + keyword) && !message.contains("不吃" + keyword)) {
                Map<String, Object> value = new LinkedHashMap<>();
                value.put("keyword", keyword);
                value.put("source", "rule");
                candidates.add(candidate("dining.preference.taste", value, 0.78));
            }
        }
    }

    private long[] parseBudgetRange(String message) {
        Matcher matcher = BUDGET_PATTERN.matcher(message);
        while (matcher.find()) {
            long value;
            try {
                String valueText = matcher.group(1) != null ? matcher.group(1) : matcher.group(3);
                value = Long.parseLong(valueText);
            } catch (NumberFormatException ignored) {
                continue;
            }
            if (value <= 0) {
                continue;
            }
            String qualifier = matcher.group(2) != null ? matcher.group(2) : matcher.group(4);
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

    private String firstKeyword(String message, List<String> keywords) {
        return keywords.stream()
                .filter(message::contains)
                .findFirst()
                .orElse("");
    }

    private boolean containsAny(String text, List<String> keywords) {
        String lower = text.toLowerCase(Locale.ROOT);
        return keywords.stream().anyMatch(keyword -> lower.contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private PreferenceMemoryCandidateDTO candidate(String key, Map<String, Object> value, double confidence) {
        PreferenceMemoryCandidateDTO candidate = new PreferenceMemoryCandidateDTO();
        candidate.setMemoryKey(key);
        candidate.setMemoryType("preference");
        candidate.setValue(value);
        candidate.setConfidence(confidence);
        candidate.setAction("UPSERT");
        return candidate;
    }
}
