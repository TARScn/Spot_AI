package com.tars.spotai.entity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ShopType {
    private Long id;
    private String name;
    private String icon;
    private Integer sort;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}
