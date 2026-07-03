package com.tars.spotai.controller;

import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatRequestDTO;
import com.tars.spotai.dto.AiChatMessageDTO;
import com.tars.spotai.dto.AiChatResponseDTO;
import com.tars.spotai.dto.AiMemoryDTO;
import com.tars.spotai.dto.ToolConfirmRequest;
import com.tars.spotai.dto.Result;
import com.tars.spotai.service.AiChatService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

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

    @GetMapping("/ai/conversations/recent")
    public Result<List<AiChatMessageDTO>> recentMessages(@RequestParam(defaultValue = "20") int limit) {
        return Result.ok(aiChatService.recentMessages(limit));
    }

    @DeleteMapping("/ai/conversations")
    public Result<Void> clearConversation() {
        aiChatService.clearConversation();
        return Result.ok(null);
    }

    @GetMapping("/ai/memories")
    public Result<List<AiMemoryDTO>> memories() {
        return Result.ok(aiChatService.memories());
    }

    @DeleteMapping("/ai/memories")
    public Result<Void> clearMemories() {
        aiChatService.clearMemories();
        return Result.ok(null);
    }

    @DeleteMapping("/ai/memories/{memoryKey}")
    public Result<Void> deleteMemory(@PathVariable String memoryKey) {
        aiChatService.deleteMemory(memoryKey);
        return Result.ok(null);
    }

    @PostMapping("/ai/tool/confirm")
    public Result<String> confirmTool(@Valid @RequestBody ToolConfirmRequest request) {
        String result = aiChatService.confirmTool(request.getConfirmToken(), request.isConfirmed());
        return Result.ok(result);
    }
}
