package com.tars.spotai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SpringAiPreferenceExtractorAgentTest {
    @Test
    void parsesFencedJsonAndFiltersLowConfidenceOrUnknownKeys() {
        String content = """
                ```json
                {"memories":[
                  {"memoryKey":"dining.preference.taste","memoryType":"preference","value":{"dislikes":["辣"]},"confidence":0.95,"action":"UPSERT"},
                  {"memoryKey":"secret.password","memoryType":"profile","value":{"text":"123"},"confidence":0.99,"action":"UPSERT"},
                  {"memoryKey":"dining.preference.area","memoryType":"preference","value":{"likes":["小寨"]},"confidence":0.3,"action":"UPSERT"}
                ]}
                ```
                """;
        SpringAiPreferenceExtractorAgent agent = new SpringAiPreferenceExtractorAgent(new FixedChatModel(content), new ObjectMapper());

        var candidates = agent.extract(1L, "我不吃辣", List.of());

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).getMemoryKey()).isEqualTo("dining.preference.taste");
        assertThat(candidates.get(0).getAction()).isEqualTo("UPSERT");
    }

    @Test
    void returnsEmptyListForInvalidJson() {
        SpringAiPreferenceExtractorAgent agent = new SpringAiPreferenceExtractorAgent(new FixedChatModel("not json"), new ObjectMapper());

        assertThat(agent.extract(1L, "随便聊聊", List.of())).isEmpty();
    }

    private static class FixedChatModel implements ChatModel {
        private final String content;

        private FixedChatModel(String content) {
            this.content = content;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
        }
    }
}
