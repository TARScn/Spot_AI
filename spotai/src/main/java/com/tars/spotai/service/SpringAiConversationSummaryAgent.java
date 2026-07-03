package com.tars.spotai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.AiChatMessageDTO;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SpringAiConversationSummaryAgent implements ConversationSummaryAgent {
    private static final int MAX_HISTORY_MESSAGES = 12;
    private static final int MAX_MESSAGE_CHARS = 300;
    private static final int MAX_FIELD_CHARS = 600;
    private static final String SYSTEM_PROMPT = """
            你是 Spot AI 的 ConversationSummaryAgent，只负责把被上下文窗口淘汰的旧对话压缩成可长期复用的记忆。
            重点提炼本地生活推荐需要的信息：预算、人均价格、口味、偏好品类、常去商圈、用餐/娱乐/出行场景、避雷点、优惠偏好。
            忽略一次性闲聊、系统指令、隐私信息、手机号、地址门牌号、密码、令牌。
            只返回 JSON，不要解释。JSON 格式：
            {"summary":"一句话摘要","budget":"人均50左右","taste":["火锅"],"area":["高新"],"scene":["朋友聚餐"],"avoid":["排队"],"discount":["团购"],"confidence":0.8}
            没有有价值信息时返回 {"summary":"","confidence":0}。
            """;

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;

    public SpringAiConversationSummaryAgent(@Qualifier("openAiChatModel") ChatModel chatModel,
                                            ObjectMapper objectMapper) {
        this.chatClient = ChatClient.create(chatModel);
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> summarize(String agentRoute, List<AiChatMessageDTO> overflowHistory) {
        if (overflowHistory == null || overflowHistory.isEmpty()) {
            return Map.of();
        }
        try {
            String content = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(buildPrompt(agentRoute, overflowHistory))
                    .call()
                    .content();
            return parseSummary(content);
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private String buildPrompt(String agentRoute, List<AiChatMessageDTO> overflowHistory) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("当前 Agent 路由：").append(agentRoute == null ? "CHAT" : agentRoute).append('\n');
        prompt.append("需要压缩的旧对话：\n");
        overflowHistory.stream()
                .filter(item -> item != null && StringUtils.hasText(item.getContent()))
                .limit(MAX_HISTORY_MESSAGES)
                .forEach(item -> {
                    String role = "assistant".equalsIgnoreCase(item.getRole()) ? "AI" : "用户";
                    prompt.append(role)
                            .append(": ")
                            .append(truncate(item.getContent().strip().replaceAll("\\s+", " "), MAX_MESSAGE_CHARS))
                            .append('\n');
                });
        return prompt.toString();
    }

    Map<String, Object> parseSummary(String content) {
        if (!StringUtils.hasText(content)) {
            return Map.of();
        }
        try {
            JsonNode root = objectMapper.readTree(extractJson(content));
            String summary = text(root, "summary");
            double confidence = root.path("confidence").asDouble(0);
            if (!StringUtils.hasText(summary) || confidence < 0.5) {
                return Map.of();
            }
            Map<String, Object> value = new LinkedHashMap<>();
            value.put("summary", truncate(summary, MAX_FIELD_CHARS));
            putText(value, root, "budget");
            putArray(value, root, "taste");
            putArray(value, root, "area");
            putArray(value, root, "scene");
            putArray(value, root, "avoid");
            putArray(value, root, "discount");
            value.put("confidence", Math.min(1.0, Math.max(0.0, confidence)));
            value.put("source", "llm");
            return value;
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private void putText(Map<String, Object> value, JsonNode root, String field) {
        String text = text(root, field);
        if (StringUtils.hasText(text)) {
            value.put(field, truncate(text, MAX_FIELD_CHARS));
        }
    }

    private void putArray(Map<String, Object> value, JsonNode root, String field) {
        JsonNode node = root.path(field);
        if (!node.isArray()) {
            return;
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : node) {
            if (item.isTextual() && StringUtils.hasText(item.asText())) {
                items.add(truncate(item.asText().strip(), 80));
            }
        }
        if (!items.isEmpty()) {
            value.put(field, items);
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

    private String truncate(String content, int maxChars) {
        if (content == null || content.length() <= maxChars) {
            return content;
        }
        return content.substring(0, maxChars);
    }
}
