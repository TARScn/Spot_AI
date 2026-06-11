package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class VoucherOrderMessage {
    private Long orderId;
    private Long userId;
    private Long voucherId;
    private Long traceId;
    private LocalDateTime createTime;
}
