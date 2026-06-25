package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AiMemoryDTO {
    private String memoryKey;
    private String memoryType;
    private String summary;
    private Double confidence;

    public static AiMemoryDTO of(String memoryKey, String memoryType, String summary, Double confidence) {
        AiMemoryDTO dto = new AiMemoryDTO();
        dto.setMemoryKey(memoryKey);
        dto.setMemoryType(memoryType);
        dto.setSummary(summary);
        dto.setConfidence(confidence);
        return dto;
    }
}
