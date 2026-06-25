package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
public class AiChatResponseDTO {
    private String answer;
    private String route;
    private String agentRoute;
    private boolean memoryUpdated;
    private List<AiMemoryDTO> memories = List.of();
    private LocalDateTime generatedAt;

    public static AiChatResponseDTO of(String answer, String route) {
        AiChatResponseDTO dto = new AiChatResponseDTO();
        dto.setAnswer(answer);
        dto.setRoute(route);
        dto.setAgentRoute(route);
        dto.setGeneratedAt(LocalDateTime.now());
        return dto;
    }

    public static AiChatResponseDTO of(String answer, String route, String agentRoute,
                                       boolean memoryUpdated, List<AiMemoryDTO> memories) {
        AiChatResponseDTO dto = of(answer, route);
        dto.setAgentRoute(agentRoute);
        dto.setMemoryUpdated(memoryUpdated);
        dto.setMemories(memories == null ? List.of() : memories);
        return dto;
    }
}
