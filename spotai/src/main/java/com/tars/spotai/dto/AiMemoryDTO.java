package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AiMemoryDTO {
    private String memoryKey;
    private String memoryType;
    private String summary;
    private Double confidence;
    private String sourceAgent;
    private LocalDateTime updateTime;

    public static AiMemoryDTO of(String memoryKey, String memoryType, String summary, Double confidence) {
        return of(memoryKey, memoryType, summary, confidence, null, null);
    }

    public static AiMemoryDTO of(String memoryKey, String memoryType, String summary, Double confidence,
                                 String sourceAgent, LocalDateTime updateTime) {
        AiMemoryDTO dto = new AiMemoryDTO();
        dto.setMemoryKey(memoryKey);
        dto.setMemoryType(memoryType);
        dto.setSummary(summary);
        dto.setConfidence(confidence);
        dto.setSourceAgent(sourceAgent);
        dto.setUpdateTime(updateTime);
        return dto;
    }
}
