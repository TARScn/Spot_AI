package com.tars.spotai.service;

import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatResponseDTO;

public interface AiChatService {
    AiChatResponseDTO chat(AiChatRequestDTO request);
}
