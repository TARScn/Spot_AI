package com.tars.spotai.service;

import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatResponseDTO;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiMemoryDTO;

import java.util.List;

public interface AiChatService {
    AiChatResponseDTO chat(AiChatRequestDTO request);

    List<AiChatMessageDTO> recentMessages(int limit);

    List<AiMemoryDTO> memories();

    void deleteMemory(String memoryKey);

    void clearMemories();

    void clearConversation();

    String confirmTool(String confirmToken, boolean confirmed);
}
