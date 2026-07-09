package com.tars.spotai.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 商户资料变更事件。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ShopChangedMessage {
    private Long shopId;
    private String action;
    private LocalDateTime eventTime;
}
