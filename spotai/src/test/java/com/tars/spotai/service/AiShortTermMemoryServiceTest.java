package com.tars.spotai.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;

import static org.assertj.core.api.Assertions.assertThat;

class AiShortTermMemoryServiceTest {

    @Test
    void storesReadsAndClearsCurrentConversationWindow() {
        AiShortTermMemoryService service = new AiShortTermMemoryService(
                MessageWindowChatMemory.builder()
                        .chatMemoryRepository(new InMemoryChatMemoryRepository())
                        .maxMessages(3)
                        .build());

        service.addUserMessage("user:99:default", "hello");
        service.addAssistantMessage("user:99:default", "hi");
        service.addUserMessage("user:99:default", "recommend shops");

        var messages = service.get("user:99:default");

        assertThat(messages).extracting("role")
                .containsExactly("user", "assistant", "user");
        assertThat(messages).extracting("content")
                .containsExactly("hello", "hi", "recommend shops");

        service.clear("user:99:default");

        assertThat(service.get("user:99:default")).isEmpty();
    }
}
