package com.tars.spotai.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class PreferenceMemoryCandidateDTO {
    private String memoryKey;
    private String memoryType;
    private Object value;
    private Double confidence;
    private String action = "UPSERT";
}
