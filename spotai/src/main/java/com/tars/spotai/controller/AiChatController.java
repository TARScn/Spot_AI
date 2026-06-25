package com.tars.spotai.controller;

import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatResponseDTO;
import com.tars.spotai.dto.Result;
import com.tars.spotai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiChatController {
    private final AiChatService aiChatService;

    public AiChatController(AiChatService aiChatService) {
        this.aiChatService = aiChatService;
    }

    @PostMapping("/ai/chat")
    public Result<AiChatResponseDTO> chat(@Valid @RequestBody AiChatRequestDTO request) {
        return Result.ok(aiChatService.chat(request));
    }
}
