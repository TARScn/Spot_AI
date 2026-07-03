package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
public class AiChatMessageDTO {
    private String role;
    private String content;
    private List<String> usedTools = List.of();
}
