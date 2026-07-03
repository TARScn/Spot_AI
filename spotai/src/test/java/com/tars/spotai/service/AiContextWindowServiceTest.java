package com.tars.spotai.service;

import com.tars.spotai.config.AiChatProperties;
import com.tars.spotai.dto.AiChatMessageDTO;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.messages.Message;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AiContextWindowServiceTest {

    @Test
    void keepsNewestMessagesWithSpringChatMemoryWindow() {
        TrackingChatMemoryRepository repository = new TrackingChatMemoryRepository();
        AiContextWindowService service = new AiContextWindowService(new AiChatProperties(), repository);

        List<AiChatMessageDTO> window = service.window(List.of(
                message("user", "old"),
                message("assistant", "middle"),
                message("user", "new")
        ), 2);

        assertThat(window).extracting(AiChatMessageDTO::getContent)
                .containsExactly("middle", "new");
        assertThat(window).extracting(AiChatMessageDTO::getRole)
                .containsExactly("assistant", "user");
    }

    @Test
    void clearsTemporaryWindowConversationAfterUse() {
        TrackingChatMemoryRepository repository = new TrackingChatMemoryRepository();
        AiContextWindowService service = new AiContextWindowService(new AiChatProperties(), repository);

        service.window(List.of(message("user", "hello")), 1);

        assertThat(repository.findConversationIds()).isEmpty();
        assertThat(repository.deletedConversationIds).hasSize(1);
        assertThat(repository.deletedConversationIds.get(0)).startsWith("window:");
    }

    @Test
    void dropsOldMessagesWhenCharacterWindowIsFull() {
        TrackingChatMemoryRepository repository = new TrackingChatMemoryRepository();
        AiContextWindowService service = new AiContextWindowService(new AiChatProperties(), repository);

        List<AiChatMessageDTO> window = service.window(List.of(
                message("user", "12345"),
                message("assistant", "abcdef"),
                message("user", "new")
        ), 5, 9);

        assertThat(window).extracting(AiChatMessageDTO::getContent)
                .containsExactly("abcdef", "new");
    }

    @Test
    void truncatesNewestMessageWhenItExceedsCharacterWindow() {
        TrackingChatMemoryRepository repository = new TrackingChatMemoryRepository();
        AiContextWindowService service = new AiContextWindowService(new AiChatProperties(), repository);

        List<AiChatMessageDTO> window = service.window(List.of(
                message("user", "old"),
                message("assistant", "123456789")
        ), 5, 4);

        assertThat(window).extracting(AiChatMessageDTO::getContent)
                .containsExactly("1234");
        assertThat(window).extracting(AiChatMessageDTO::getRole)
                .containsExactly("assistant");
    }

    private static AiChatMessageDTO message(String role, String content) {
        AiChatMessageDTO message = new AiChatMessageDTO();
        message.setRole(role);
        message.setContent(content);
        return message;
    }

    private static class TrackingChatMemoryRepository implements ChatMemoryRepository {
        private final Map<String, List<Message>> messages = new LinkedHashMap<>();
        private final List<String> deletedConversationIds = new ArrayList<>();

        @Override
        public List<String> findConversationIds() {
            return new ArrayList<>(messages.keySet());
        }

        @Override
        public List<Message> findByConversationId(String conversationId) {
            return messages.getOrDefault(conversationId, List.of());
        }

        @Override
        public void saveAll(String conversationId, List<Message> messages) {
            this.messages.put(conversationId, new ArrayList<>(messages));
        }

        @Override
        public void deleteByConversationId(String conversationId) {
            messages.remove(conversationId);
            deletedConversationIds.add(conversationId);
        }
    }
}
