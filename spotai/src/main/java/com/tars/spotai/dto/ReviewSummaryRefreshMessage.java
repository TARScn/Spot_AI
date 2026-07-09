package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 店铺评价摘要刷新事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReviewSummaryRefreshMessage {
    private Long shopId;
    private String reason;
    private LocalDateTime eventTime;
}
