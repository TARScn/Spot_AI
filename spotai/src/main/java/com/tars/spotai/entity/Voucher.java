package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class Voucher {
    private Long id;
    private Long shopId;
    private String title;
    private String subTitle;
    private String rules;
    private Long payValue;
    private Long actualValue;
    private Integer type;
    private Integer status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
