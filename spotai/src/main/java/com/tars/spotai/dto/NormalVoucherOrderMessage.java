package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 普通代金券领取订单事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class NormalVoucherOrderMessage {
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private Long traceId;
    private LocalDateTime createTime;
}
