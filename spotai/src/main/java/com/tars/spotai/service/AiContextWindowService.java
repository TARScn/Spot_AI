package com.tars.spotai.service;

import com.tars.spotai.config.AiChatProperties;
import com.tars.spotai.dto.AiChatMessageDTO;
import org.springframework.ai.chat.memory.ChatMemoryRepository;
import org.springframework.ai.chat.memory.InMemoryChatMemoryRepository;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.UUID;

@Component
public class AiContextWindowService {
    private final AiChatProperties properties;
    private final ChatMemoryRepository chatMemoryRepository;

    @Autowired
    public AiContextWindowService(AiChatProperties properties, ChatMemoryRepository chatMemoryRepository) {
        this.properties = properties;
        this.chatMemoryRepository = chatMemoryRepository;
    }

    public AiContextWindowService(AiChatProperties properties) {
        this(properties, new InMemoryChatMemoryRepository());
    }

    AiContextWindowService() {
        this(new AiChatProperties());
    }

    public List<AiChatMessageDTO> window(List<AiChatMessageDTO> history) {
        return window(history, properties.safeContextWindowMaxMessages(), properties.safeContextWindowMaxChars());
    }

    public List<AiChatMessageDTO> window(List<AiChatMessageDTO> history, int maxMessages) {
        return window(history, maxMessages, properties.safeContextWindowMaxChars());
    }

    public List<AiChatMessageDTO> window(List<AiChatMessageDTO> history, int maxMessages, int maxChars) {
        if (history == null || history.isEmpty()) {
            return List.of();
        }
        MessageWindowChatMemory chatMemory = MessageWindowChatMemory.builder()
                .chatMemoryRepository(chatMemoryRepository)
                .maxMessages(Math.max(1, maxMessages))
                .build();
        String conversationId = "window:" + UUID.randomUUID();
        try {
            chatMemory.add(conversationId, history.stream()
                    .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                    .map(this::toSpringMessage)
                    .toList());
            List<AiChatMessageDTO> messageWindow = chatMemory.get(conversationId).stream()
                    .map(this::toDto)
                    .toList();
            return fitCharWindow(messageWindow, maxChars);
        } finally {
            chatMemory.clear(conversationId);
        }
    }

    private List<AiChatMessageDTO> fitCharWindow(List<AiChatMessageDTO> messages, int maxChars) {
        if (messages == null || messages.isEmpty()) {
            return List.of();
        }
        int safeMaxChars = Math.max(1, maxChars);
        List<AiChatMessageDTO> selected = new java.util.ArrayList<>();
        int totalChars = 0;
        for (int i = messages.size() - 1; i >= 0; i--) {
            AiChatMessageDTO message = messages.get(i);
            if (message == null || !StringUtils.hasText(message.getContent())) {
                continue;
            }
            int messageChars = message.getContent().length();
            if (!selected.isEmpty() && totalChars + messageChars > safeMaxChars) {
                break;
            }
            AiChatMessageDTO next = copy(message);
            if (selected.isEmpty() && messageChars > safeMaxChars) {
                next.setContent(message.getContent().substring(0, safeMaxChars));
                messageChars = safeMaxChars;
            }
            selected.add(0, next);
            totalChars += messageChars;
        }
        return selected;
    }

    private AiChatMessageDTO copy(AiChatMessageDTO source) {
        AiChatMessageDTO target = new AiChatMessageDTO();
        target.setRole(source.getRole());
        target.setContent(source.getContent());
        return target;
    }

    private Message toSpringMessage(AiChatMessageDTO message) {
        if ("assistant".equalsIgnoreCase(message.getRole())) {
            return new AssistantMessage(message.getContent());
        }
        return new UserMessage(message.getContent());
    }

    private AiChatMessageDTO toDto(Message message) {
        AiChatMessageDTO dto = new AiChatMessageDTO();
        dto.setRole(message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user");
        dto.setContent(message.getText());
        return dto;
    }
}
