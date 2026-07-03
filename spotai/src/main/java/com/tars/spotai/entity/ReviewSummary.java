package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ReviewSummary {
    private Long shopId;
    private String status;
    private String summary;
    private String highlightsJson;
    private String weaknessesJson;
    private String scenesJson;
    private Integer reviewCount;
    private Integer version;
    private LocalDateTime generatedAt;
    private LocalDateTime expireAt;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
