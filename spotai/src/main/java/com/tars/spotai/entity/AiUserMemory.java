package com.tars.spotai.entity;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
public class AiUserMemory {
    private Long id;
    private Long userId;
    private String memoryKey;
    private String memoryType;
    private String memoryJson;
    private Double confidence;
    private Long sourceMessageId;
    private String sourceAgent;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
