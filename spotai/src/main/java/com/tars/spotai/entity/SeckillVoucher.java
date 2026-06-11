package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeckillVoucher {
    private Long id;
    private Long voucherId;
    private Integer initStock;
    private Integer stock;
    private String allowedLevels;
    private Integer minLevel;
    private LocalDateTime createTime;
    private LocalDateTime beginTime;
    private LocalDateTime endTime;
    private LocalDateTime updateTime;
}
