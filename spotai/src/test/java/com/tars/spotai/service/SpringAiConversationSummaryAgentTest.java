package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tars.spotai.dto.AiChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiConversationSummaryAgentTest {

    @Test
    void summarizesOverflowHistoryIntoStructuredMemory() {
        CapturingChatModel chatModel = new CapturingChatModel("""
                ```json
                {"summary":"用户偏好高新人均50左右的安静火锅店","budget":"人均50左右","taste":["火锅"],"area":["高新"],"scene":["朋友聚餐"],"avoid":["排队"],"discount":["团购"],"confidence":0.86,"extra":"ignored"}
                ```
                """);
        SpringAiConversationSummaryAgent agent = new SpringAiConversationSummaryAgent(chatModel, new ObjectMapper());

        Map<String, Object> summary = agent.summarize("SHOP_GUIDE", List.of(
                message("user", "我想找高新人均50左右的火锅，最好安静点"),
                message("assistant", "可以优先看高新附近的火锅店")
        ));

        assertThat(summary)
                .containsEntry("summary", "用户偏好高新人均50左右的安静火锅店")
                .containsEntry("budget", "人均50左右")
                .containsEntry("source", "llm");
        assertThat(summary.get("taste")).isEqualTo(List.of("火锅"));
        assertThat(summary.get("area")).isEqualTo(List.of("高新"));
        assertThat(summary).doesNotContainKey("extra");
        assertThat(chatModel.prompt.getContents()).contains("当前 Agent 路由：SHOP_GUIDE");
    }

    @Test
    void ignoresLowConfidenceOrEmptySummary() {
        SpringAiConversationSummaryAgent agent = new SpringAiConversationSummaryAgent(
                new CapturingChatModel("{\"summary\":\"只是闲聊\",\"confidence\":0.2}"),
                new ObjectMapper());

        Map<String, Object> summary = agent.summarize("CHAT", List.of(message("user", "你好")));

        assertThat(summary).isEmpty();
    }

    private static AiChatMessageDTO message(String role, String content) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static class CapturingChatModel implements ChatModel {
        private final String content;
        private Prompt prompt;

        private CapturingChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            this.prompt = prompt;
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
    }
}
