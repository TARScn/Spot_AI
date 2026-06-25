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
import java.util.List;
import java.util.Locale;

@Service
public class SpringAiPreferenceExtractorAgent implements PreferenceExtractorAgent {
    private static final int MAX_MESSAGE_CHARS = 1200;
    private static final int MAX_VALUE_CHARS = 600;
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
        String content = chatClient.prompt()
                .system(SYSTEM_PROMPT)
                .user(buildPrompt(userId, message, existingMemories))
                .call()
                .content();
        return parseCandidates(content);
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
}
