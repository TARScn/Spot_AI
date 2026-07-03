package com.tars.spotai.service;

import com.tars.spotai.dto.AiChatMessageDTO;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

@Service
public class AiShortTermMemoryService {
    private final ChatMemory chatMemory;

    public AiShortTermMemoryService(ChatMemory chatMemory) {
        this.chatMemory = chatMemory;
    }

    public List<AiChatMessageDTO> get(String conversationId) {
        if (!StringUtils.hasText(conversationId)) {
            return List.of();
        }
        return chatMemory.get(conversationId).stream()
                .map(this::toDto)
                .toList();
    }

    public void addUserMessage(String conversationId, String content) {
        add(conversationId, new UserMessage(content));
    }

    public void addAssistantMessage(String conversationId, String content) {
        add(conversationId, new AssistantMessage(content));
    }

    public void clear(String conversationId) {
        if (StringUtils.hasText(conversationId)) {
            chatMemory.clear(conversationId);
        }
    }

    private void add(String conversationId, Message message) {
        if (!StringUtils.hasText(conversationId) || message == null || !StringUtils.hasText(message.getText())) {
            return;
        }
        chatMemory.add(conversationId, message);
    }

    private AiChatMessageDTO toDto(Message message) {
        AiChatMessageDTO dto = new AiChatMessageDTO();
        dto.setRole(message.getMessageType() == MessageType.ASSISTANT ? "assistant" : "user");
        dto.setContent(message.getText());
        return dto;
    }
}
